package hu.bme.mit.theta.common.ltl

import hu.bme.mit.theta.analysis.expl.ExplAnalysis
import hu.bme.mit.theta.analysis.expl.ExplPrec
import hu.bme.mit.theta.analysis.expl.ExplState
import hu.bme.mit.theta.analysis.expl.ItpRefToExplPrec
import hu.bme.mit.theta.analysis.stmtoptimizer.DefaultStmtOptimizer
import hu.bme.mit.theta.analysis.unit.UnitState
import hu.bme.mit.theta.common.logging.ConsoleLogger
import hu.bme.mit.theta.common.logging.Logger
import hu.bme.mit.theta.solver.ItpSolver
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.z3.Z3SolverFactory
import hu.bme.mit.theta.xsts.XSTS
import hu.bme.mit.theta.xsts.analysis.*
import hu.bme.mit.theta.xsts.dsl.XstsDslManager
import junit.framework.TestCase.fail
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.FileInputStream

@RunWith(Parameterized::class)
class LtlCheckTestWithXsts(
    private val xstsName: String,
    private val ltlExpr: String,
    private val result: Boolean
) {

    private val itpSolver: ItpSolver = Z3SolverFactory.getInstance().createItpSolver()
    private val abstractionSolver: Solver = Z3SolverFactory.getInstance().createSolver()
    private val logger: Logger = ConsoleLogger(Logger.Level.VERBOSE)

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data() = listOf(
            arrayOf("counter3inf", "F G(x=3)", true),
            arrayOf("counter3inf", "F(x=2)", true),
            arrayOf("counter3inf", "G(x<4)", true),
            arrayOf("counter3inf", "G(x=1)", false),
            arrayOf("counter6to7", "G(x=1)", false),
            arrayOf("counter6to7", "G(x=7)", false),
            arrayOf("counter6to7", "G F(x=7)", true),
        )
    }

    @Test
    fun test() {
        var xstsI: XSTS?
        FileInputStream(String.format("src/test/resources/xsts/%s.xsts", xstsName))
            .use { inputStream ->
            xstsI = XstsDslManager.createXsts(inputStream)
        }
        if (xstsI == null)
            fail("Couldn't read xsts $xstsName")
        val xsts = xstsI!!
        val dataAnalysis = ExplAnalysis.create(
            abstractionSolver,
            xsts.initFormula
        )
        val analysis = XstsAnalysis.create(
            dataAnalysis
        )
        val lts = XstsLts.create(xsts, XstsStmtOptimizer.create(DefaultStmtOptimizer.create<ExplState>()))
        val refToPrec = ItpRefToExplPrec()
        val initFunc = XstsInitFunc.create{ _: ExplPrec -> listOf<UnitState>(UnitState.getInstance()) }
        val variables = xsts.vars
        val initPrec = ExplPrec.of(variables)
        val combineStates = { x: XstsState<UnitState>, d: ExplState -> XstsState.of(d, x.lastActionWasEnv(), true) }
        val stripState = { x: XstsState<ExplState> -> XstsState.of(UnitState.getInstance(), x.lastActionWasEnv(), true) }
        val extractFromState = { x: XstsState<ExplState> -> x.state }
        val stripPrec = { p: ExplPrec -> p }

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
            logger
        )

        Assert.assertEquals(result, checkResult.isEmpty)
    }

}