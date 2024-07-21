package hu.bme.mit.theta.common.ltl

import hu.bme.mit.theta.analysis.algorithm.loopchecker.abstraction.LoopcheckerSearchStrategy
import hu.bme.mit.theta.analysis.algorithm.loopchecker.refinement.LDGTraceCheckerStrategy
import hu.bme.mit.theta.common.logging.ConsoleLogger
import hu.bme.mit.theta.common.logging.Logger
import hu.bme.mit.theta.solver.z3legacy.Z3LegacySolverFactory
import hu.bme.mit.theta.xsts.XSTS
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
    private val searchStrategy: LoopcheckerSearchStrategy,
    private val refinerStrategy: LDGTraceCheckerStrategy,
) {

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
        listOf(LoopcheckerSearchStrategy.GDFS, /*LoopcheckerSearchStrategy.NDFS*/).flatMap { search ->
            LDGTraceCheckerStrategy.entries.flatMap { ref ->
                data().map {
                    arrayOf(*it, search, ref)
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
        val solverFactory = Z3LegacySolverFactory.getInstance()
        val configBuilder = XstsConfigBuilder(XstsConfigBuilder.Domain.PRED_SPLIT, XstsConfigBuilder.Refinement.SEQ_ITP, solverFactory, solverFactory).initPrec(
            XstsConfigBuilder.InitPrec.EMPTY).predSplit(XstsConfigBuilder.PredSplit.ATOMS).PredStrategy(xsts)
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
        )
        val checkResult = checker.check(initPrec, initPrec)

        Assert.assertEquals(result, checkResult.isSafe)
    }

}