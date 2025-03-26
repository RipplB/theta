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
package hu.bme.mit.theta.analysis.algorithm.bounded.pipeline

import hu.bme.mit.theta.analysis.Trace
import hu.bme.mit.theta.analysis.algorithm.Proof
import hu.bme.mit.theta.analysis.algorithm.SafetyChecker
import hu.bme.mit.theta.analysis.algorithm.SafetyResult
import hu.bme.mit.theta.analysis.algorithm.bounded.MonolithicExpr
import hu.bme.mit.theta.analysis.algorithm.bounded.pipeline.constraints.PrimeMEPassValidator
import hu.bme.mit.theta.analysis.algorithm.bounded.pipeline.constraints.VariableConsistencyMEPassValidator
import hu.bme.mit.theta.analysis.expl.ExplState
import hu.bme.mit.theta.analysis.expr.ExprAction
import hu.bme.mit.theta.analysis.unit.UnitPrec
import hu.bme.mit.theta.common.logging.Logger
import hu.bme.mit.theta.common.logging.NullLogger

typealias PipelineStep<Pr> = Pair<Int, MonolithicExprPassResult<Pr>>

enum class PipelineDirection(val invert: () -> PipelineDirection, val indexModifier: (Int) -> Int) {
  FORWARD({ BACKWARD }, { it + 1 }),
  BACKWARD({ FORWARD }, { it - 1 }),
}

class MonolithicExprPassPipelineChecker<Pr : Proof>(
  val model: MonolithicExpr,
  val checkerFactory: (MonolithicExpr) -> SafetyChecker<Pr, Trace<ExplState, ExprAction>, UnitPrec>,
  private val passes: MutableList<MonolithicExprPass<Pr>> = mutableListOf(),
  private val logger: Logger = NullLogger.getInstance(),
  private val validators: List<MonolithicExprPassValidator<in Pr>> =
    listOf(VariableConsistencyMEPassValidator, PrimeMEPassValidator),
) : SafetyChecker<Pr, Trace<ExplState, ExprAction>, UnitPrec> {

  private val steps: MutableList<PipelineStep<Pr>> = mutableListOf()

  fun registerPass(pass: MonolithicExprPass<Pr>) {
    passes.add(pass)
  }

  fun insertPass(pass: MonolithicExprPass<Pr>, index: Int) {
    if (index >= passes.size) {
      return registerPass(pass)
    }
    passes.add(index, pass)
  }

  override fun check(input: UnitPrec?): SafetyResult<Pr, Trace<ExplState, ExprAction>> {
    var result = MonolithicExprPassResult<Pr>(model)
    var status = MonolithicExprPipelineCheckerStatus(0, 0, true)
    var componentIndex = 0
    var direction = PipelineDirection.FORWARD

    // Loop implementation to avoid deep stacks of recursion
    while (true) {
      if (componentIndex == -1) return result.safetyResult!!

      if (componentIndex == passes.size) {
        // reached the end of the pipeline, call checker
        val checkerRes = checkerFactory(result.expressionResult!!).check(input)

        // update status and result than invert the pipeline
        status = status.copy(checksRan = status.checksRan + 1, invertedSinceLastCheck = 0)
        result = result.copy(safetyResult = checkerRes, direction = PipelineDirection.BACKWARD)
        steps.addLast(Pair(componentIndex, result))
        direction = PipelineDirection.BACKWARD
        componentIndex--
        continue
      }

      // call the current pass than validate and log its result
      result = passes[componentIndex].process(result, status)
      steps.addLast(Pair(componentIndex, result))
      validators.forEach { it.checkStepResult(steps) }

      if (result.direction != direction) {
        // current pass indicates a direction change
        direction = direction.invert()
        status = status.copy(invertedSinceLastCheck = status.invertedSinceLastCheck + 1)
      }

      componentIndex = direction.indexModifier(componentIndex)
    }
  }
}

/**
 * A status object that holds information about the current state of the pipeline and runs along the
 * execution
 */
data class MonolithicExprPipelineCheckerStatus(
  val invertedSinceLastCheck: Int,
  val checksRan: Int,
  val providesDirection: Boolean,
)
