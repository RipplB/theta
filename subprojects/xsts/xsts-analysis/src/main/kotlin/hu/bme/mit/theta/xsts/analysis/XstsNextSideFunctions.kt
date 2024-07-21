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

package hu.bme.mit.theta.xsts.analysis

import hu.bme.mit.theta.analysis.State
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.analysis.multi.MultiSide
import hu.bme.mit.theta.analysis.multi.MultiState
import hu.bme.mit.theta.analysis.unit.UnitState
import hu.bme.mit.theta.analysis.multi.NextSideFunctions.NextSideFunction as NextsideFunction

class SingleXstsNextSideFunction<L : State, R : State, D : State, M : MultiState<L, R, D>>(
    private val getXstsState: (M) -> XstsState<*>, private val xstsSide: MultiSide
) : NextsideFunction<L, R, D, M> {

    override fun defineNextSide(state: M): MultiSide {
        if (state.sourceSide != xstsSide) return xstsSide
        val xstsState = getXstsState(state)
        return if (xstsState.lastActionWasEnv()) xstsSide else xstsSide.otherSide()
    }

}

class BothXstsNextSideFunction<D : ExprState, M : MultiState<XstsState<UnitState>, XstsState<UnitState>, D>> :
    NextsideFunction<XstsState<UnitState>, XstsState<UnitState>, D, M> {

    override fun defineNextSide(state: M): MultiSide {
        val lastState: XstsState<UnitState> =
            if (state.sourceSide == MultiSide.LEFT) state.leftState else state.rightState
        return if (lastState.lastActionWasEnv()) state.sourceSide else state.sourceSide.otherSide()
    }
}