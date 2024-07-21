package hu.bme.mit.theta.common.ltl

import hu.bme.mit.theta.analysis.algorithm.loopchecker.abstraction.LoopcheckerSearchStrategy
import hu.bme.mit.theta.analysis.algorithm.loopchecker.refinement.LDGTraceCheckerStrategy
import hu.bme.mit.theta.analysis.expl.ExplAnalysis
import hu.bme.mit.theta.analysis.expl.ExplPrec
import hu.bme.mit.theta.analysis.expl.ExplState
import hu.bme.mit.theta.analysis.expl.ItpRefToExplPrec
import hu.bme.mit.theta.analysis.multi.MultiAnalysisSide
import hu.bme.mit.theta.analysis.multi.MultiPrec
import hu.bme.mit.theta.analysis.stmtoptimizer.DefaultStmtOptimizer
import hu.bme.mit.theta.analysis.unit.UnitState
import hu.bme.mit.theta.cfa.analysis.CfaPrec
import hu.bme.mit.theta.cfa.analysis.prec.GlobalCfaPrec
import hu.bme.mit.theta.common.logging.ConsoleLogger
import hu.bme.mit.theta.common.logging.Logger
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.z3legacy.Z3LegacySolverFactory
import hu.bme.mit.theta.xsts.XSTS
import hu.bme.mit.theta.xsts.analysis.*
import hu.bme.mit.theta.xsts.analysis.config.XstsConfigBuilder
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
        FileInputStream("src/test/resources/xsts/$xstsName.xsts")
            .use { inputStream ->
            xstsI = XstsDslManager.createXsts(inputStream)
        }
        if (xstsI == null)
            fail("Couldn't read xsts $xstsName")
        val xsts = xstsI!!
        val solverFactory = Z3LegacySolverFactory.getInstance()
        val configBuilder = XstsConfigBuilder(XstsConfigBuilder.Domain.EXPL, XstsConfigBuilder.Refinement.SEQ_ITP, solverFactory, solverFactory).initPrec(XstsConfigBuilder.InitPrec.EMPTY).ExplStrategy(xsts)
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
            LoopcheckerSearchStrategy.GDFS,
            LDGTraceCheckerStrategy.MILANO
        )
        val checkResult = checker.check(initPrec, initPrec)

        Assert.assertEquals(result, checkResult.isSafe)
    }

}