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
package hu.bme.mit.theta.sts.analysis

import hu.bme.mit.theta.analysis.Trace
import hu.bme.mit.theta.analysis.algorithm.InvariantProof
import hu.bme.mit.theta.analysis.algorithm.SafetyChecker
import hu.bme.mit.theta.analysis.algorithm.bounded.MonolithicExpr
import hu.bme.mit.theta.analysis.algorithm.bounded.pipeline.MEPipelineCheckerConstructorArguments
import hu.bme.mit.theta.analysis.algorithm.bounded.pipeline.MonolithicExprPass
import hu.bme.mit.theta.analysis.algorithm.bounded.pipeline.passes.AbstractionMEPass
import hu.bme.mit.theta.analysis.expl.ExplState
import hu.bme.mit.theta.analysis.expr.ExprAction
import hu.bme.mit.theta.analysis.expr.refinement.ExprTraceFwBinItpChecker
import hu.bme.mit.theta.analysis.pred.PredPrec
import hu.bme.mit.theta.analysis.unit.UnitPrec
import hu.bme.mit.theta.common.Utils
import hu.bme.mit.theta.common.logging.Logger
import hu.bme.mit.theta.core.type.Expr
import hu.bme.mit.theta.core.type.booltype.BoolExprs
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.solver.SolverFactory
import hu.bme.mit.theta.solver.z3legacy.Z3LegacySolverFactory
import hu.bme.mit.theta.sts.STS
import hu.bme.mit.theta.sts.aiger.AigerParser
import hu.bme.mit.theta.sts.aiger.AigerToSts
import hu.bme.mit.theta.sts.analysis.pipeline.StsPipelineChecker
import hu.bme.mit.theta.sts.dsl.StsDslManager
import java.io.FileInputStream
import java.io.IOException
import org.junit.Assert
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class StsAbstractMonolithicTest(private val filePath: String, private val expectedResult: Boolean) {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{index}: {0}, {1}")
    fun data(): Collection<Array<Any>> {
      return listOf(

        //                    {"src/test/resources/hw1_false.aag", false},
        //                    {"src/test/resources/hw2_true.aag", true},
        //                    {"src/test/resources/boolean1.system", false},
        //                    {"src/test/resources/boolean2.system", false},
        arrayOf("src/test/resources/counter.system", true),
        arrayOf("src/test/resources/counter_bad.system", false),
        arrayOf("src/test/resources/counter_parametric.system", true),

        //                {"src/test/resources/loop.system", true},
        arrayOf("src/test/resources/loop_bad.system", false),
        arrayOf("src/test/resources/multipleinitial.system", false),
        arrayOf("src/test/resources/readerswriters.system", true),
        arrayOf("src/test/resources/simple1.system", false),
        arrayOf("src/test/resources/simple2.system", true),
        arrayOf("src/test/resources/simple3.system", false),
      )
    }
  }

  @Throws(IOException::class)
  private fun runTest(
    logger: Logger?,
    solverFactory: SolverFactory?,
    checkerBuilderFunction:
      (MonolithicExpr) -> SafetyChecker<InvariantProof, Trace<ExplState, ExprAction>, UnitPrec>,
  ) {
    val sts: STS
    if (filePath.endsWith("aag")) {
      sts = AigerToSts.createSts(AigerParser.parse(filePath))
    } else {
      val spec = StsDslManager.createStsSpec(FileInputStream(filePath))
      if (spec.getAllSts().size != 1) {
        throw UnsupportedOperationException("STS contains multiple properties.")
      }
      sts = Utils.singleElementOf<STS?>(spec.getAllSts())
    }

    val passes =
      mutableListOf<MonolithicExprPass<InvariantProof>>(
        AbstractionMEPass<PredPrec, InvariantProof>(
          PredPrec.of(),
          { monolithicExpr: MonolithicExpr? ->
            ExprTraceFwBinItpChecker.create(
              monolithicExpr!!.initExpr,
              BoolExprs.Not(monolithicExpr.propExpr),
              Z3LegacySolverFactory.getInstance().createItpSolver(),
            )
          },
          { prec: PredPrec?, expr: Expr<BoolType>? -> prec!!.join(PredPrec.of(expr)) },
        )
      )

    val checker =
      StsPipelineChecker(sts, MEPipelineCheckerConstructorArguments(checkerBuilderFunction, passes))

    Assert.assertEquals(expectedResult, checker.check().isSafe())
  }
}
