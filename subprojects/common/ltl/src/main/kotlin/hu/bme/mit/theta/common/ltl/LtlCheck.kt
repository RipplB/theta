package hu.bme.mit.theta.common.ltl

import hu.bme.mit.theta.analysis.Analysis
import hu.bme.mit.theta.analysis.InitFunc
import hu.bme.mit.theta.analysis.LTS
import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.algorithm.SafetyResult
import hu.bme.mit.theta.analysis.algorithm.loopchecker.AcceptancePredicate
import hu.bme.mit.theta.analysis.algorithm.loopchecker.LDGCegarVerifier
import hu.bme.mit.theta.analysis.algorithm.loopchecker.RefinerStrategy
import hu.bme.mit.theta.analysis.algorithm.loopchecker.SearchStrategy
import hu.bme.mit.theta.analysis.expr.ExprAction
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.analysis.expr.refinement.ItpRefutation
import hu.bme.mit.theta.analysis.expr.refinement.RefutationToPrec
import hu.bme.mit.theta.analysis.multi.*
import hu.bme.mit.theta.analysis.unit.UnitState
import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.CfaAction
import hu.bme.mit.theta.cfa.analysis.CfaAnalysis
import hu.bme.mit.theta.cfa.analysis.CfaPrec
import hu.bme.mit.theta.cfa.analysis.CfaState
import hu.bme.mit.theta.cfa.analysis.prec.GlobalCfaPrec
import hu.bme.mit.theta.cfa.analysis.prec.RefutationToGlobalCfaPrec
import hu.bme.mit.theta.cfa.buchi.BuchiBuilder
import hu.bme.mit.theta.common.logging.Logger
import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolExprs.True
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.solver.ItpSolver

object LtlCheck {
    fun <RState : ExprState, RBlank : ExprState, RAction : ExprAction, RPrec : Prec, RBlankPrec : Prec, DataPrec : Prec, DataState : ExprState> check(
        formalismAnalysis: Analysis<RState, RAction, RPrec>,
        lts: LTS<in RState, RAction>,
        refToPrec: RefutationToPrec<RPrec, ItpRefutation>,
        dataAnalysis: Analysis<DataState, in CfaAction, DataPrec>,
        dataRefToPrec: RefutationToPrec<DataPrec, ItpRefutation>,
        initFunc: InitFunc<RBlank, RBlankPrec>,
        initPrec: RPrec,
        initExpr: Expr<BoolType>?,
        dataInitPrec: DataPrec,
        variables: Collection<VarDecl<*>>,
        combineStates: (blankState: RBlank, dataState: DataState) -> RState,
        stripState: (combinedState: RState) -> RBlank,
        extractDataFromCombinedState: (combinedState: RState) -> DataState,
        stripPrecision: (combinedPrec: RPrec) -> RBlankPrec,
        ltl: String,
        solver: ItpSolver,
        logger: Logger,
        searchStrategy: SearchStrategy,
        refinerStrategy: RefinerStrategy
    ): SafetyResult<ExprMultiState<RBlank, CfaState<UnitState>, DataState>, ExprMultiAction<RAction, CfaAction>> {
        return check(
            formalismAnalysis,
            lts,
            refToPrec,
            dataAnalysis,
            dataRefToPrec,
            initFunc,
            initPrec,
            initExpr,
            dataInitPrec,
            variables,
            combineStates,
            stripState,
            extractDataFromCombinedState,
            stripPrecision,
            ::alternatingNextSide,
            ltl,
            solver,
            logger,
            searchStrategy,
            refinerStrategy
        )
    }

    fun <RState : ExprState, RBlank : ExprState, RAction : ExprAction, RPrec : Prec, RBlankPrec : Prec, DataPrec : Prec, DataState : ExprState> check(
        formalismAnalysis: Analysis<RState, RAction, RPrec>,
        lts: LTS<in RState, RAction>,
        refToPrec: RefutationToPrec<RPrec, ItpRefutation>,
        dataAnalysis: Analysis<DataState, in CfaAction, DataPrec>,
        dataRefToPrec: RefutationToPrec<DataPrec, ItpRefutation>,
        initFunc: InitFunc<RBlank, RBlankPrec>,
        initPrec: RPrec,
        initExpr: Expr<BoolType>?,
        dataInitPrec: DataPrec,
        variables: Collection<VarDecl<*>>,
        combineStates: (blankState: RBlank, dataState: DataState) -> RState,
        stripState: (combinedState: RState) -> RBlank,
        extractDataFromCombinedState: (combinedState: RState) -> DataState,
        stripPrecision: (combinedPrec: RPrec) -> RBlankPrec,
        defineNextSide: (state: MultiState<RBlank, CfaState<UnitState>, DataState>) -> MultiSourceSide,
        ltl: String,
        solver: ItpSolver,
        logger: Logger,
        searchStrategy: SearchStrategy,
        refinerStrategy: RefinerStrategy
    ): SafetyResult<ExprMultiState<RBlank, CfaState<UnitState>, DataState>, ExprMultiAction<RAction, CfaAction>> {
        val buchiAutomaton = BuchiBuilder.of(ltl, variables, logger)
        val cfaAnalysis : Analysis<CfaState<DataState>, CfaAction, CfaPrec<DataPrec>> = CfaAnalysis.create(buchiAutomaton.initLoc, dataAnalysis)
        val product = MultiBuilder
            .initWithLeftSide(formalismAnalysis, combineStates, stripState, extractDataFromCombinedState, lts, initFunc, stripPrecision)
            .addRightSide(cfaAnalysis, BuchiLts(), ::combineBlankBuchiStateWithData, ::stripDataFromBuchiState, { s -> s.state}, BuchiInitFunc.of(buchiAutomaton.initLoc), { p -> p.getPrec(buchiAutomaton.initLoc)})
            .build<DataPrec, ExprMultiState<RBlank, CfaState<UnitState>, DataState>, ExprMultiAction<RAction, CfaAction>>(defineNextSide, dataAnalysis.initFunc, {ls, rs, dns, dif -> ExprMultiAnalysis.of(ls, rs, dns, dif)}, {llts, cls, rlts, crs, dns -> ExprMultiLts.of(llts, cls, rlts, crs, dns)})
        val buchiRefToPrec : RefutationToPrec<CfaPrec<DataPrec>, ItpRefutation> = RefutationToGlobalCfaPrec(dataRefToPrec, buchiAutomaton.initLoc)
        val multiRefToPrec = RefToMultiPrec(refToPrec, buchiRefToPrec, dataRefToPrec)
        val verifier = LDGCegarVerifier.of(product.analysis, product.lts, buchiPredicate(buchiAutomaton), logger, solver, initExpr ?: True(), multiRefToPrec)
        return verifier.verify(MultiPrec(initPrec, GlobalCfaPrec.create(dataInitPrec), dataInitPrec),
            searchStrategy, refinerStrategy
        )
    }

    private fun <D : ExprState> combineBlankBuchiStateWithData (buchiState: CfaState<UnitState>, dataState: D) : CfaState<D> {
        return CfaState.of(buchiState.loc, dataState)
    }

    private fun <D : ExprState> stripDataFromBuchiState (buchiState: CfaState<D>) : CfaState<UnitState> {
        return CfaState.of(buchiState.loc, UnitState.getInstance())
    }

    @JvmStatic
    fun alternatingNextSide(state: MultiState<*, *, *>) : MultiSourceSide {
        return if (state.sourceSide == MultiSourceSide.LEFT) MultiSourceSide.RIGHT else MultiSourceSide.LEFT
    }

    private fun <D : ExprState, S : ExprState, A : ExprAction> buchiPredicate(buchiAutomaton: CFA) : AcceptancePredicate<ExprMultiState<S, CfaState<UnitState>, D>, ExprMultiAction<A, CfaAction>> {
        return AcceptancePredicate.ofActionPredicate { a -> (a.rightAction != null && a.rightAction.edges.any { e -> buchiAutomaton.acceptingEdges.contains(e) }) }
    }

}
