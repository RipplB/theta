/*
 *  Copyright 2024 Budapest University of Technology and Economics
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package hu.bme.mit.theta.common.ltl

import hu.bme.mit.theta.analysis.*
import hu.bme.mit.theta.analysis.algorithm.SafetyResult
import hu.bme.mit.theta.analysis.algorithm.arg.ARG
import hu.bme.mit.theta.analysis.algorithm.loopchecker.AcceptancePredicate
import hu.bme.mit.theta.analysis.algorithm.loopchecker.LDGCegarVerifier
import hu.bme.mit.theta.analysis.algorithm.loopchecker.RefinerStrategy
import hu.bme.mit.theta.analysis.algorithm.loopchecker.SearchStrategy
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.analysis.expr.refinement.ItpRefutation
import hu.bme.mit.theta.analysis.expr.refinement.RefutationToPrec
import hu.bme.mit.theta.analysis.multi.*
import hu.bme.mit.theta.analysis.multi.builder.stmt.StmtMultiBuilder
import hu.bme.mit.theta.analysis.multi.stmt.ExprMultiState
import hu.bme.mit.theta.analysis.multi.stmt.StmtMultiAction
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
    fun <RState : ExprState, RBlank : ExprState, RAction : StmtAction, RPrec : Prec, RBlankPrec : Prec, DataPrec : Prec, DataState : ExprState> check(
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
    ): SafetyResult<ARG<ExprMultiState<RBlank, CfaState<UnitState>, DataState>, StmtMultiAction<RAction, CfaAction>>, Trace<ExprMultiState<RBlank, CfaState<UnitState>, DataState>, StmtMultiAction<RAction, CfaAction>>> {
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

    fun <RState : ExprState, RBlank : ExprState, RAction : StmtAction, RPrec : Prec, RBlankPrec : Prec, DataPrec : Prec, DataState : ExprState> check(
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
        defineNextSide: (state: MultiState<RBlank, CfaState<UnitState>, DataState>) -> MultiSide,
        ltl: String,
        solver: ItpSolver,
        logger: Logger,
        searchStrategy: SearchStrategy,
        refinerStrategy: RefinerStrategy
    ): SafetyResult<ARG<ExprMultiState<RBlank, CfaState<UnitState>, DataState>, StmtMultiAction<RAction, CfaAction>>, Trace<ExprMultiState<RBlank, CfaState<UnitState>, DataState>, StmtMultiAction<RAction, CfaAction>>> {
        val buchiAutomaton = BuchiBuilder.of(ltl, variables, logger)
        val cfaAnalysis : Analysis<CfaState<DataState>, CfaAction, CfaPrec<DataPrec>> = CfaAnalysis.create(buchiAutomaton.initLoc, dataAnalysis)
        val leftSide = MultiAnalysisSide(
            formalismAnalysis,
            initFunc,
            combineStates,
            stripState,
            extractDataFromCombinedState,
            stripPrecision
        )
        val rightSide = MultiAnalysisSide(
            cfaAnalysis,
            BuchiInitFunc.of(buchiAutomaton.initLoc),
            ::combineBlankBuchiStateWithData,
            ::stripDataFromBuchiState,
            { s -> s.state},
            { p -> p.getPrec(buchiAutomaton.initLoc)}
        )
        val product = StmtMultiBuilder(leftSide, lts)
            .addRightSide(rightSide, BuchiLts())
            .build(defineNextSide, dataAnalysis.initFunc)
        val buchiRefToPrec : RefutationToPrec<CfaPrec<DataPrec>, ItpRefutation> = RefutationToGlobalCfaPrec(dataRefToPrec, buchiAutomaton.initLoc)
        val multiRefToPrec = RefToMultiPrec(refToPrec, buchiRefToPrec, dataRefToPrec)
        val verifier = LDGCegarVerifier.of(product.side.analysis, product.lts, buchiPredicate(buchiAutomaton), logger, solver, initExpr ?: True(), multiRefToPrec)
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
    fun alternatingNextSide(state: MultiState<*, *, *>) : MultiSide {
        return if (state.sourceSide == MultiSide.LEFT) MultiSide.RIGHT else MultiSide.LEFT
    }

    private fun <D : ExprState, S : ExprState, A : StmtAction> buchiPredicate(buchiAutomaton: CFA) : AcceptancePredicate<ExprMultiState<S, CfaState<UnitState>, D>, StmtMultiAction<A, CfaAction>> {
        return AcceptancePredicate.ofActionPredicate { a -> (a.rightAction != null && a.rightAction.edges.any { e -> buchiAutomaton.acceptingEdges.contains(e) }) }
    }

}
