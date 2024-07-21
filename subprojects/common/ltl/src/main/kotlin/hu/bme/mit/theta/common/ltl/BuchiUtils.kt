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

import hu.bme.mit.theta.analysis.InitFunc
import hu.bme.mit.theta.analysis.LTS
import hu.bme.mit.theta.analysis.Prec
import hu.bme.mit.theta.analysis.algorithm.loopchecker.AcceptancePredicate
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.analysis.multi.stmt.ExprMultiState
import hu.bme.mit.theta.analysis.multi.stmt.StmtMultiAction
import hu.bme.mit.theta.analysis.unit.UnitState
import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.CfaAction
import hu.bme.mit.theta.cfa.analysis.CfaState

class BuchiInitFunc<P : Prec>(private val initLoc: CFA.Loc) : InitFunc<CfaState<UnitState>, P> {

    override fun getInitStates(prec: P) = mutableListOf(CfaState.of(initLoc, UnitState.getInstance()))
}

class BuchiLts<D : ExprState> : LTS<CfaState<D>, CfaAction> {

    override fun getEnabledActionsFor(state: CfaState<D>) = state.loc.outEdges.map(CfaAction::create)
}

fun <S : ExprState, D : ExprState, A : StmtAction> buchiPredicate(
    buchiAutomaton: CFA
): AcceptancePredicate<ExprMultiState<S, CfaState<UnitState>, D>, StmtMultiAction<A, CfaAction>> = AcceptancePredicate(
    actionPredicate = { a ->
        (a?.rightAction != null && a.rightAction.edges.any { e ->
            buchiAutomaton.acceptingEdges.contains(
                e
            )
        })
    })

fun <D : ExprState> combineBlankBuchiStateWithData(buchiState: CfaState<UnitState>, dataState: D) =
    CfaState.of(buchiState.loc, dataState)

fun <D : ExprState> stripDataFromBuchiState(buchiState: CfaState<D>) =
    CfaState.of(buchiState.loc, UnitState.getInstance())