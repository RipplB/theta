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

import hu.bme.mit.theta.analysis.algorithm.loopchecker.abstraction.LoopcheckerSearchStrategy
import hu.bme.mit.theta.analysis.algorithm.loopchecker.refinement.LDGTraceCheckerStrategy
import hu.bme.mit.theta.analysis.multi.MultiSide
import hu.bme.mit.theta.analysis.multi.NextSideFunctions
import hu.bme.mit.theta.common.logging.ConsoleLogger
import hu.bme.mit.theta.common.logging.Logger
import hu.bme.mit.theta.solver.SolverManager
import hu.bme.mit.theta.solver.javasmt.JavaSMTSolverManager
import hu.bme.mit.theta.solver.smtlib.SmtLibSolverManager
import hu.bme.mit.theta.solver.z3legacy.Z3SolverManager
import hu.bme.mit.theta.xsts.XSTS
import hu.bme.mit.theta.xsts.analysis.SingleXstsNextSideFunction
import hu.bme.mit.theta.xsts.analysis.config.XstsConfigBuilder
import hu.bme.mit.theta.xsts.dsl.XstsDslManager
import junit.framework.TestCase.fail
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.FileInputStream

@RunWith(Parameterized::class)
class LtlCheckTestWithXstsPred(
    private val xstsName: String,
    private val ltlExpr: String,
    private val result: Boolean,
    private val combineSteps: Boolean,
    private val searchStrategy: LoopcheckerSearchStrategy,
    private val refinerStrategy: LDGTraceCheckerStrategy,
) {

    private val logger: Logger = ConsoleLogger(Logger.Level.INFO)

    companion object {

        private fun data() = listOf(
            arrayOf("simple_types", "F G(color = Colors.Red)", false), // OK
            arrayOf("counter3inf", "F G(x=3)", true), // OK
            arrayOf("counter3inf", "F(x=2)", true), // OK
            arrayOf("counter3inf", "G(x<4)", true), // OK
            arrayOf("counter3inf", "G(x=1)", false), // OK
            arrayOf("counter6to7", "G(x=1)", false), // OK
            arrayOf("counter6to7", "G(x=7)", false), // OK
            arrayOf("counter6to7", "G F(x=7)", true), // OK
            arrayOf("counter50", "G(x<49)", false), // OK
            arrayOf(
                "trafficlight_v2", "G(LightCommands_displayRed -> X(not LightCommands_displayGreen))", true
            ), // OK
            arrayOf(
                "trafficlight_v2",
                "G((normal = Normal.Red and (not PoliceInterrupt_police) and Control_toggle) -> X(normal = Normal.Green or interrupted = Interrupted.BlinkingYellow))",
                true, true
            ), // OK
            arrayOf("trafficlight_v2", "G(PoliceInterrupt_police -> F(LightCommands_displayYellow))", true), // OK
            arrayOf("forever5", "G(x=5)", true), // OK
            arrayOf("forever5", "F(x=6)", false), // OK
            arrayOf("randomincreasingeven", "not F(x=1)", true), // Stuck in iteration 12 in GDFS
            arrayOf("randomincreasingeven", "F(x>10)", true), // OK
            arrayOf("randomincreasingeven", "G(x>=0)", true), // OK
            arrayOf("simple_color", "G(envColor = Colors.Green -> X(modelColor = Colors.Blue))", true), // OK
            arrayOf("simple_color", "G(envColor = Colors.Green -> X(modelColor = Colors.Green))", false), // OK
            arrayOf("simple_color", "F G(envColor = modelColor)", false), // OK
            arrayOf("weather", "G F(isClever and isWet)", false), // timeout in GDFS
//            arrayOf("weather", "F G(not isWet)", true), // theory not supported by interpolation or bad proof in GDFS
            arrayOf(
                "weather", "G(time = TimeOfDay.Noon -> X(time = TimeOfDay.Noon or time = TimeOfDay.Afternoon))", true
            ), // OK
//            arrayOf("weather", "F G(weather = Weather.Foggy -> (clothing = Clothing.Nothing or clothing = Clothing.Warm))", true),
//            arrayOf("weather_noinit", "G F(isClever and isWet)", false),
//            arrayOf("weather_noinit", "F G(not isWet)", true),
//            arrayOf("weather_noinit", "G(time = TimeOfDay.Noon -> X(time = TimeOfDay.Noon or time = TimeOfDay.Afternoon))", true),
//            arrayOf("weather_noinit", "F G(weather = Weather.Foggy -> (clothing = Clothing.Nothing or clothing = Clothing.Warm))", true),
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0} ({1}): {4}-{5}")
        fun params() =
            data().map { if (it.size == 3) arrayOf(*it, false) else it }.flatMap { data ->
                listOf(/*LoopcheckerSearchStrategy.GDFS,*/ LoopcheckerSearchStrategy.NDFS).flatMap { search ->
                    listOf(LDGTraceCheckerStrategy.MILANO, LDGTraceCheckerStrategy.BOUNDED_UNROLLING).map { ref ->
                        arrayOf(*data, search, ref)
                    }
                }
            }
    }

    @Test
    fun test() {
        var xstsI: XSTS?
        FileInputStream("src/test/resources/xsts/$xstsName.xsts")
            .use { inputStream ->
                xstsI = XstsDslManager.createXsts(inputStream)
            }
        if (xstsI == null)
            fail("Couldn't read xsts $xstsName")
        val xsts = xstsI!!
        SolverManager.registerSolverManager(JavaSMTSolverManager.create())
        SolverManager.registerSolverManager(SmtLibSolverManager.create(SmtLibSolverManager.HOME, logger))
        SolverManager.registerSolverManager(Z3SolverManager.create())
        val solverFactory = SolverManager.resolveSolverFactory("Z3")
        val configBuilder = XstsConfigBuilder(
            XstsConfigBuilder.Domain.PRED_SPLIT, XstsConfigBuilder.Refinement.SEQ_ITP, solverFactory, solverFactory
        ).initPrec(
            XstsConfigBuilder.InitPrec.EMPTY
        ).predSplit(XstsConfigBuilder.PredSplit.ATOMS).PredStrategy(xsts)
        val initPrec = configBuilder.initPrec

        val checker = LtlChecker(
            configBuilder.multiSide,
            configBuilder.lts,
            configBuilder.itpRefToPrec,
            configBuilder.itpRefToPrec,
            configBuilder.dataAnalysis,
            xsts.vars,
            ltlExpr,
            solverFactory,
            logger,
            searchStrategy,
            refinerStrategy,
            xsts.initFormula,
            if (combineSteps) SingleXstsNextSideFunction(
                { it.leftState }, MultiSide.LEFT
            ) else NextSideFunctions.Alternating()
        )
        val checkResult = checker.check(initPrec, initPrec)

        Assert.assertEquals(result, checkResult.isSafe)
    }

}