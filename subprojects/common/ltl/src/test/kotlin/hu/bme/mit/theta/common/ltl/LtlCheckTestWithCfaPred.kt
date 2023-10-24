package hu.bme.mit.theta.common.ltl

import hu.bme.mit.theta.analysis.algorithm.loopchecker.RefinerStrategy
import hu.bme.mit.theta.analysis.algorithm.loopchecker.SearchStrategy
import hu.bme.mit.theta.analysis.expr.ExprAction
import hu.bme.mit.theta.analysis.pred.*
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
class LtlCheckTestWithCfaPred(
    private val cfaName: String,
    private val ltlExpr: String,
    private val result: Boolean
) {

    private val itpSolver: ItpSolver = Z3SolverFactory.getInstance().createItpSolver()
    private val abstractionSolver: Solver = Z3SolverFactory.getInstance().createSolver()
    private val logger: Logger = ConsoleLogger(Logger.Level.INFO)

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data() = listOf(
                arrayOf("wave_flags", "F G(x)", false),
                arrayOf("wave_flags", "F G(x and y)", false),
                arrayOf("wave_flag", "G F(x)", true),
                arrayOf("wave_flag", "G(x)", false),
                arrayOf("wave_flag", "F G(x)", false),
                arrayOf("counter5inf", "G(not(x=6))", true),
                arrayOf("counter5inf", "G(x=1)", false),
                arrayOf("counter5inf", "G(x=5)", false),
                arrayOf("counter5inf", "F G(x=5)", true),
                arrayOf("counter5inf", "F(x=1)", true),
                arrayOf("counter5inf", "F(x=5)", true),
                arrayOf("wave_flags", "G F(y)", true),
                arrayOf("wave_flags", "F G(x)", false),
                arrayOf("indicator", "G (x -> y)", true),
//                arrayOf("indicator_multiassign", "G (x -> y)", true),
                arrayOf("indicator", "G (x -> X (not x))", true),
                arrayOf("indicator", "G (y -> X (not y))", false),
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
        val dataAnalysis = PredAnalysis.create<ExprAction>(
            abstractionSolver,
            PredAbstractors.booleanSplitAbstractor(abstractionSolver),
            True()
        )
        val analysis = CfaAnalysis.create(cfa.initLoc, dataAnalysis)
        val lts: CfaLts = CfaSbeLts.getInstance()
//        val lts: CfaLts = CfaLbeLts.of(cfa.initLoc)
        val refToPrec = RefutationToGlobalCfaPrec(ItpRefToPredPrec(ExprSplitters.atoms()), cfa.initLoc)
        val initFunc = { _: CfaPrec<PredPrec> -> listOf<CfaState<UnitState>>(CfaState.of(cfa.initLoc, UnitState.getInstance())) }
        val variables = cfa.vars
        val dataInitPrec = PredPrec.of()
        val initPrec:CfaPrec<PredPrec> = GlobalCfaPrec.create(dataInitPrec)
        val combineStates = { c: CfaState<UnitState>, d: PredState -> CfaState.of(c.loc, d) }
        val stripState = { c: CfaState<PredState> -> CfaState.of(c.loc, UnitState.getInstance()) }
        val extractFromState = { c: CfaState<PredState> -> c.state }
        val stripPrec = { p: CfaPrec<PredPrec> -> p }

        val checkResult = LtlCheck.check(
            analysis,
            lts,
            refToPrec,
            dataAnalysis,
            ItpRefToPredPrec(ExprSplitters.atoms()),
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