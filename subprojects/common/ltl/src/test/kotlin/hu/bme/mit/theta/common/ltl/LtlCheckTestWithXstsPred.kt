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

import hu.bme.mit.theta.analysis.algorithm.loopchecker.RefinerStrategy
import hu.bme.mit.theta.analysis.algorithm.loopchecker.SearchStrategy
import hu.bme.mit.theta.analysis.expr.ExprAction
import hu.bme.mit.theta.analysis.pred.*
import hu.bme.mit.theta.analysis.stmtoptimizer.DefaultStmtOptimizer
import hu.bme.mit.theta.analysis.unit.UnitState
import hu.bme.mit.theta.common.logging.ConsoleLogger
import hu.bme.mit.theta.common.logging.Logger
import hu.bme.mit.theta.solver.ItpSolver
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.z3legacy.Z3LegacySolverFactory
import hu.bme.mit.theta.xsts.XSTS
import hu.bme.mit.theta.xsts.analysis.*
import hu.bme.mit.theta.xsts.dsl.XstsDslManager
import hu.bme.mit.theta.xsts.normalize
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
    private val searchStrategy: SearchStrategy,
    private val refinerStrategy: RefinerStrategy
) {

    private val itpSolver: ItpSolver = Z3LegacySolverFactory.getInstance().createItpSolver()
    private val abstractionSolver: Solver = Z3LegacySolverFactory.getInstance().createSolver()
    private val logger: Logger = ConsoleLogger(Logger.Level.INFO)

    companion object {
        private fun data() = listOf(
            arrayOf("simple_types", "F G(color = Colors.Red)", false),
            arrayOf("counter3inf", "F G(x=3)", true),
            arrayOf("counter3inf", "F(x=2)", true),
            arrayOf("counter3inf", "G(x<4)", true),
            arrayOf("counter3inf", "G(x=1)", false),
            arrayOf("counter6to7", "G(x=1)", false),
            arrayOf("counter6to7", "G(x=7)", false),
            arrayOf("counter6to7", "G F(x=7)", true),
//            arrayOf("counter50", "G(x<49)", false),
            arrayOf("trafficlight_v2", "G(LightCommands_displayRed -> X(not LightCommands_displayGreen))", true),
            arrayOf("trafficlight_v2", "G((normal = Normal.Red and (not PoliceInterrupt_police) and Control_toggle) -> X(normal = Normal.Green))", true),
            arrayOf("trafficlight_v2", "G(PoliceInterrupt_police -> F(LightCommands_displayYellow))", true),
            arrayOf("forever5", "G(x=5)", true),
            arrayOf("forever5", "F(x=6)", false),
            arrayOf("randomincreasingeven", "not F(x=1)", true),
            arrayOf("randomincreasingeven", "F(x>10)", true),
            arrayOf("randomincreasingeven", "G(x>=0)", true),
            arrayOf("simple_color", "G(envColor = Colors.Green -> X(modelColor = Colors.Blue))", true),
            arrayOf("simple_color", "G(envColor = Colors.Green -> X(modelColor = Colors.Green))", false),
            arrayOf("simple_color", "F G(envColor = modelColor)", false),
            arrayOf("weather", "G F(isClever and isWet)", false),
            arrayOf("weather", "F G(not isWet)", true),
            arrayOf("weather", "G(time = TimeOfDay.Noon -> X(time = TimeOfDay.Noon or time = TimeOfDay.Afternoon))", true),
//            arrayOf("weather", "F G(weather = Weather.Foggy -> (clothing = Clothing.Nothing or clothing = Clothing.Warm))", true),
//            arrayOf("weather_noinit", "G F(isClever and isWet)", false),
//            arrayOf("weather_noinit", "F G(not isWet)", true),
//            arrayOf("weather_noinit", "G(time = TimeOfDay.Noon -> X(time = TimeOfDay.Noon or time = TimeOfDay.Afternoon))", true),
//            arrayOf("weather_noinit", "F G(weather = Weather.Foggy -> (clothing = Clothing.Nothing or clothing = Clothing.Warm))", true),
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{3}-{4}: {0}")
        fun params() =
        listOf(SearchStrategy.DFS, SearchStrategy.NDFS).flatMap { search ->
            RefinerStrategy.values().flatMap { ref ->
                data().map {
                    arrayOf(*it, search, ref)
                }
            }
        }
    }

    @Test
    fun test() {
        var xstsI: XSTS?
        FileInputStream(String.format("src/test/resources/xsts/%s.xsts", xstsName)).use { inputStream ->
            xstsI = XstsDslManager.createXsts(inputStream)
        }
        if (xstsI == null)
            fail("Couldn't read xsts $xstsName")
        val xsts = normalize(xstsI)
        val dataAnalysis = PredAnalysis.create<ExprAction>(
            abstractionSolver,
            PredAbstractors.booleanSplitAbstractor(abstractionSolver),
            xsts.initFormula
        )
        val analysis = XstsAnalysis.create(
            dataAnalysis
        )
        val lts = XstsLts.create(xsts, XstsStmtOptimizer.create(DefaultStmtOptimizer.create<PredState>()))
        val refToPrec = ItpRefToPredPrec(ExprSplitters.atoms())
        val initFunc = XstsInitFunc.create{ _: PredPrec -> listOf<UnitState>(UnitState.getInstance()) }
        val initPrec = PredPrec.of()
        val variables = xsts.vars
        val combineStates = { x: XstsState<UnitState>, d: PredState -> XstsState.of(d, x.lastActionWasEnv(), true) }
        val stripState = { x: XstsState<PredState> -> XstsState.of(UnitState.getInstance(), x.lastActionWasEnv(), true) }
        val extractFromState = { x: XstsState<PredState> -> x.state }
        val stripPrec = { p: PredPrec -> p }

        val checkResult = LtlCheck.check(
            analysis,
            lts,
            refToPrec,
            dataAnalysis,
            refToPrec,
            initFunc,
            initPrec,
            xsts.initFormula,
            initPrec,
            variables,
            combineStates,
            stripState,
            extractFromState,
            stripPrec,
            ltlExpr,
            itpSolver,
            logger,
            searchStrategy,
            refinerStrategy
        )

        Assert.assertEquals(result, checkResult.isSafe)
    }

}