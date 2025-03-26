/*
 *  Copyright 2025 Budapest University of Technology and Economics
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
package hu.bme.mit.theta.analysis.algorithm.bounded.pipeline.passes

import hu.bme.mit.theta.analysis.Trace
import hu.bme.mit.theta.analysis.algorithm.Proof
import hu.bme.mit.theta.analysis.algorithm.SafetyResult
import hu.bme.mit.theta.analysis.algorithm.bounded.MonolithicExpr
import hu.bme.mit.theta.analysis.algorithm.bounded.pipeline.DirectionalMonolithicExprPass
import hu.bme.mit.theta.analysis.algorithm.bounded.pipeline.MonolithicExprPassResult
import hu.bme.mit.theta.analysis.expl.ExplState
import hu.bme.mit.theta.analysis.expr.ExprAction
import hu.bme.mit.theta.core.type.booltype.BoolExprs.Not
import hu.bme.mit.theta.core.utils.ExprUtils

class ReverseMEPass<Pr : Proof> : DirectionalMonolithicExprPass<Pr> {

  override fun forward(monolithicExpr: MonolithicExpr): MonolithicExprPassResult<Pr> {
    return MonolithicExprPassResult(
      monolithicExpr.let {
        it.copy(
          initExpr = Not(it.propExpr),
          transExpr = ExprUtils.reverse(it.transExpr, it.transOffsetIndex),
          propExpr = Not(it.initExpr),
        )
      }
    )
  }

  override fun backward(
    safetyResult: SafetyResult<Pr, Trace<ExplState, ExprAction>>
  ): MonolithicExprPassResult<Pr> {
    if (safetyResult.isUnsafe) {
      return MonolithicExprPassResult(
        SafetyResult.unsafe(safetyResult.asUnsafe().cex.reverse(), safetyResult.asUnsafe().proof)
      )
    }
    return MonolithicExprPassResult(safetyResult)
  }
}
