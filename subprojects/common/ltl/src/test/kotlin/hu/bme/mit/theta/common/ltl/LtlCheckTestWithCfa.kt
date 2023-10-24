package hu.bme.mit.theta.common.ltl

import hu.bme.mit.theta.analysis.algorithm.loopchecker.RefinerStrategy
import hu.bme.mit.theta.analysis.algorithm.loopchecker.SearchStrategy
import hu.bme.mit.theta.analysis.expl.ExplAnalysis
import hu.bme.mit.theta.analysis.expl.ExplPrec
import hu.bme.mit.theta.analysis.expl.ExplState
import hu.bme.mit.theta.analysis.expl.ItpRefToExplPrec
import hu.bme.mit.theta.analysis.unit.UnitState
import hu.bme.mit.theta.cfa.CFA
import hu.bme.mit.theta.cfa.analysis.CfaAnalysis
import hu.bme.mit.theta.cfa.analysis.CfaPrec
import hu.bme.mit.theta.cfa.analysis.CfaState
import hu.bme.mit.theta.cfa.analysis.lts.CfaLts
import hu.bme.mit.theta.cfa.analysis.lts.CfaSbeLts
import hu.bme.mit.theta.cfa.analysis.prec.GlobalCfaPrec
import hu.bme.mit.theta.cfa.analysis.prec.RefutationToGlobalCfaPrec
import hu.bme.mit.theta.cfa.dsl.CfaDslManager
import hu.bme.mit.theta.common.logging.ConsoleLogger
import hu.bme.mit.theta.common.logging.Logger
import hu.bme.mit.theta.core.type.booltype.BoolExprs.True
import hu.bme.mit.theta.solver.ItpSolver
import hu.bme.mit.theta.solver.Solver
import hu.bme.mit.theta.solver.z3.Z3SolverFactory
import junit.framework.TestCase.fail
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.FileInputStream

@RunWith(Parameterized::class)
class LtlCheckTestWithCfa(
    private val cfaName: String,
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
            arrayOf("counter2inf", "G(x=1)", false),
            arrayOf("counter2inf", "G(x=2)", false),
            arrayOf("counter2inf", "F G(x=2)", true),
            arrayOf("counter2inf", "F(x=1)", true),
            arrayOf("counter2inf", "F(x=3)", false),
        )
    }

    @Test
    fun test() {
        itpSolver.reset()
        abstractionSolver.reset()
        var cfaI: CFA?
        FileInputStream(String.format("src/test/resources/cfa/%s.cfa", cfaName)).use { inputStream ->
            cfaI = CfaDslManager.createCfa(inputStream)
        }
        if (cfaI == null)
            fail("Couldn't read cfa $cfaName")
        val cfa = cfaI!!
        val dataAnalysis = ExplAnalysis.create(
            abstractionSolver,
            True()
        )
        val analysis = CfaAnalysis.create(cfa.initLoc, dataAnalysis)
        val lts: CfaLts = CfaSbeLts.getInstance()
        val refToPrec = RefutationToGlobalCfaPrec(ItpRefToExplPrec(), cfa.initLoc)
        val initFunc = { _: CfaPrec<ExplPrec> -> listOf<CfaState<UnitState>>(CfaState.of(cfa.initLoc, UnitState.getInstance())) }
        val variables = cfa.vars
        val dataInitPrec = ExplPrec.of(variables)
        val initPrec:CfaPrec<ExplPrec> = GlobalCfaPrec.create(dataInitPrec)
        val combineStates = { c: CfaState<UnitState>, d: ExplState -> CfaState.of(c.loc, d) }
        val stripState = { c: CfaState<ExplState> -> CfaState.of(c.loc, UnitState.getInstance()) }
        val extractFromState = { c: CfaState<ExplState> -> c.state }
        val stripPrec = { p: CfaPrec<ExplPrec> -> p }

        val checkResult = LtlCheck.check(
            analysis,
            lts,
            refToPrec,
            dataAnalysis,
            ItpRefToExplPrec(),
            initFunc,
            initPrec,
            True(),
            dataInitPrec,
            variables,
            combineStates,
            stripState,
            extractFromState,
            stripPrec,
            ltlExpr,
            itpSolver,
            logger,
            SearchStrategy.DFS,
            RefinerStrategy.MILANO
        )

        Assert.assertEquals(result, checkResult.isSafe)
    }

}