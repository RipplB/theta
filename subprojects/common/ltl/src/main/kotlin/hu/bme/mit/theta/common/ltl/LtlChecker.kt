package hu.bme.mit.theta.common.ltl

import hu.bme.mit.theta.analysis.*
import hu.bme.mit.theta.analysis.algorithm.ARG
import hu.bme.mit.theta.analysis.algorithm.SafetyChecker
import hu.bme.mit.theta.analysis.algorithm.SafetyResult
import hu.bme.mit.theta.analysis.algorithm.loopchecker.RefinerStrategy
import hu.bme.mit.theta.analysis.algorithm.loopchecker.SearchStrategy
import hu.bme.mit.theta.analysis.expr.ExprAction
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.analysis.expr.refinement.ItpRefutation
import hu.bme.mit.theta.analysis.expr.refinement.RefutationToPrec
import hu.bme.mit.theta.analysis.multi.MultiAction
import hu.bme.mit.theta.analysis.multi.MultiSourceSide
import hu.bme.mit.theta.analysis.multi.MultiState
import hu.bme.mit.theta.analysis.unit.UnitState
import hu.bme.mit.theta.cfa.analysis.CfaAction
import hu.bme.mit.theta.cfa.analysis.CfaState
import hu.bme.mit.theta.common.logging.Logger
import hu.bme.mit.theta.common.logging.NullLogger
import hu.bme.mit.theta.core.decl.VarDecl
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.solver.ItpSolver

class LtlChecker<RState : ExprState, RBlank : ExprState, RAction : ExprAction, RPrec : Prec, RBlankPrec : Prec, DataPrec : Prec, DataState : ExprState> (
    private val formalismAnalysis: Analysis<RState, RAction, RPrec>,
    private val lts: LTS<in RState, RAction>,
    private val refToPrec: RefutationToPrec<RPrec, ItpRefutation>,
    private val dataAnalysis: Analysis<DataState, in CfaAction, DataPrec>,
    private val dataRefToPrec: RefutationToPrec<DataPrec, ItpRefutation>,
    private val initFunc: InitFunc<RBlank, RBlankPrec>,
    private val initExpr: Expr<BoolType>? = null,
    private val dataInitPrec: DataPrec,
    private val variables: Collection<VarDecl<*>>,
    private val combineStates: (RBlank, DataState) -> RState,
    private val stripState: (RState) -> RBlank,
    private val extractDataFromCombinedState: (RState) -> DataState,
    private val stripPrecision: (RPrec) -> RBlankPrec,
    private val defineNextSide: (MultiState<RBlank, CfaState<UnitState>, DataState>) -> MultiSourceSide = LtlCheck::alternatingNextSide,
    private val ltl: String,
    private val solver: ItpSolver,
    private val searchStrategy: SearchStrategy = SearchStrategy.defaultValue(),
    private val refinerStrategy: RefinerStrategy = RefinerStrategy.defaultValue(),
    private val logger: Logger = NullLogger.getInstance(),
)  : SafetyChecker<RState, RAction, RPrec> {
    override fun check(prec: RPrec): SafetyResult<RState, RAction> {
        val safetyResult = LtlCheck.check(
            formalismAnalysis,
            lts,
            refToPrec,
            dataAnalysis,
            dataRefToPrec,
            initFunc,
            prec,
            initExpr,
            dataInitPrec,
            variables,
            combineStates,
            stripState,
            extractDataFromCombinedState,
            stripPrecision,
            defineNextSide,
            ltl,
            solver,
            logger,
            searchStrategy,
            refinerStrategy
        )
        val mockArg = ARG.create<RState, RAction> { _, _ -> true }
        val hasStats = safetyResult.stats.isPresent
        if (safetyResult.isSafe) {
            if (hasStats)
                return SafetyResult.safe(mockArg, safetyResult.stats.get())
            return SafetyResult.safe(mockArg)
        }
        val convertTrace = convertTrace(safetyResult.asUnsafe().trace)
        if (hasStats)
            return SafetyResult.unsafe(convertTrace, mockArg, safetyResult.stats.get())
        return SafetyResult.unsafe(convertTrace, mockArg)
    }

    private fun convertTrace(multiTrace: Trace<out MultiState<RBlank, CfaState<UnitState>, DataState>, out MultiAction<RAction, CfaAction>>) : Trace<RState, RAction> {
        val firstState = multiTrace.getState(0)
        val states = mutableListOf(combineStates(firstState.leftState, firstState.dataState))
        val actions = mutableListOf<RAction>()
        for (i in 1..multiTrace.length()) {
            val currentState = multiTrace.getState(i)
            val currentAction = multiTrace.getAction(i - 1)
            if (currentAction.leftAction == null)
                continue
            states.add(combineStates(currentState.leftState, currentState.dataState))
            actions.add(currentAction.leftAction)
        }
        return Trace.of(states, actions)
    }

}