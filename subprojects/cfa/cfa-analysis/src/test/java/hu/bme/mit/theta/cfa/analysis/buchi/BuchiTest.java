package hu.bme.mit.theta.cfa.analysis.buchi;

import hu.bme.mit.theta.analysis.Analysis;
import hu.bme.mit.theta.analysis.Trace;
import hu.bme.mit.theta.analysis.algorithm.loopchecker.AcceptancePredicate;
import hu.bme.mit.theta.analysis.algorithm.loopchecker.LDGCegarVerifier;
import hu.bme.mit.theta.analysis.algorithm.loopchecker.RefinerStrategy;
import hu.bme.mit.theta.analysis.algorithm.loopchecker.SearchStrategy;
import hu.bme.mit.theta.analysis.expr.refinement.ItpRefutation;
import hu.bme.mit.theta.analysis.expr.refinement.RefutationToPrec;
import hu.bme.mit.theta.analysis.pred.*;
import hu.bme.mit.theta.cfa.CFA;
import hu.bme.mit.theta.cfa.analysis.CfaAction;
import hu.bme.mit.theta.cfa.analysis.CfaAnalysis;
import hu.bme.mit.theta.cfa.analysis.CfaPrec;
import hu.bme.mit.theta.cfa.analysis.CfaState;
import hu.bme.mit.theta.cfa.analysis.config.CfaConfigBuilder;
import hu.bme.mit.theta.cfa.analysis.lts.CfaLts;
import hu.bme.mit.theta.cfa.analysis.prec.GlobalCfaPrec;
import hu.bme.mit.theta.cfa.analysis.prec.RefutationToGlobalCfaPrec;
import hu.bme.mit.theta.cfa.dsl.CfaDslManager;
import hu.bme.mit.theta.common.logging.ConsoleLogger;
import hu.bme.mit.theta.common.logging.Logger;
import hu.bme.mit.theta.solver.ItpSolver;
import hu.bme.mit.theta.solver.Solver;
import hu.bme.mit.theta.solver.z3.Z3SolverFactory;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;

import static hu.bme.mit.theta.core.type.booltype.BoolExprs.True;

public class BuchiTest {

	private static Solver abstractionSolver;
	private static ItpSolver itpSolver;
	private static Logger logger;

	@BeforeClass
	public static void init() {
		abstractionSolver = Z3SolverFactory.getInstance().createSolver();
		itpSolver = Z3SolverFactory.getInstance().createItpSolver();
		logger = new ConsoleLogger(Logger.Level.INFO);
	}

	@Test
	public void testTransitionOnCounter5() throws IOException {
		final CFA cfa = CfaDslManager.createCfa(new FileInputStream("src/test/resources/counter5_true.cfa"));
		for (CFA.Edge edge :
				cfa.getEdges()) {
			if (edge.getSource().getName().equals("L1") && edge.getTarget().getName().equals("L2")) {
				cfa.getAcceptingEdges().add(edge);
				break;
			}
		}
		final CfaLts lts = CfaConfigBuilder.Encoding.SBE.getLts(cfa.getErrorLoc().orElse(null));
		final Analysis<CfaState<PredState>, CfaAction, CfaPrec<PredPrec>> analysis = CfaAnalysis
				.create(cfa.getInitLoc(), PredAnalysis.create(abstractionSolver, PredAbstractors.booleanSplitAbstractor(abstractionSolver), True()));
		final AcceptancePredicate<CfaState<PredState>, CfaAction> target = BuchiAcceptance.transitionBased(cfa);
		final RefutationToPrec<PredPrec, ItpRefutation> refToPrec = new ItpRefToPredPrec(ExprSplitters.atoms());
		final RefutationToGlobalCfaPrec<PredPrec, ItpRefutation> cfaRefToPrec = new RefutationToGlobalCfaPrec<>(refToPrec, cfa.getInitLoc());
		final LDGCegarVerifier<CfaState<PredState>, CfaAction, CfaPrec<PredPrec>> verifier = LDGCegarVerifier.of(analysis, lts, target, logger, itpSolver, cfaRefToPrec);

		final GlobalCfaPrec<PredPrec> prec = GlobalCfaPrec.create(PredPrec.of());
		Optional<Trace<CfaState<PredState>, CfaAction>> res = verifier.verify(prec, SearchStrategy.DFS, RefinerStrategy.MILANO);
		Assert.assertFalse(res.isPresent());
	}

}
