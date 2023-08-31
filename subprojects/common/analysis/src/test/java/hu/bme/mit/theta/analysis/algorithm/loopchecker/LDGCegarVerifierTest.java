/*
 *  Copyright 2023 Budapest University of Technology and Economics
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
package hu.bme.mit.theta.analysis.algorithm.loopchecker;

import hu.bme.mit.theta.analysis.Analysis;
import hu.bme.mit.theta.analysis.LTS;
import hu.bme.mit.theta.analysis.Trace;
import hu.bme.mit.theta.analysis.expr.ExprStatePredicate;
import hu.bme.mit.theta.analysis.expr.refinement.ItpRefutation;
import hu.bme.mit.theta.analysis.expr.refinement.RefutationToPrec;
import hu.bme.mit.theta.analysis.pred.*;
import hu.bme.mit.theta.analysis.stmtoptimizer.DefaultStmtOptimizer;
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
import hu.bme.mit.theta.xsts.XSTS;
import hu.bme.mit.theta.xsts.analysis.*;
import hu.bme.mit.theta.xsts.dsl.XstsDslManager;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;

import static hu.bme.mit.theta.core.type.booltype.BoolExprs.True;

@RunWith(Parameterized.class)
public class LDGCegarVerifierTest {

	private static Solver abstractionSolver;
	private static ItpSolver itpSolver;
	private static Logger logger;

	@BeforeClass
	public static void init() {
		abstractionSolver = Z3SolverFactory.getInstance().createSolver();
		itpSolver = Z3SolverFactory.getInstance().createItpSolver();
		logger = new ConsoleLogger(Logger.Level.INFO);
	}

	@Parameterized.Parameter
	public String fileName;

	@Parameterized.Parameter(1)
	public String propFileName;

	@Parameterized.Parameter(2)
	public String acceptingLocationName;

	@Parameterized.Parameter(3)
	public boolean result;

	@Parameterized.Parameters
	public static Collection<Object[]> data() {
		return Arrays.asList(new Object[][]{
				{"counter3.xsts", "x_eq_3.prop", "", false}
				, {"counter6to7.xsts", "x_eq_7.prop", "", true}
				, {"counter6to7.cfa", "", "IS6", true}
				, {"counter2to3.cfa", "", "IS3", true}
				, {"counter6to7.xsts", "x_eq_6.prop", "", true}
				, {"infinitehavoc.xsts", "x_eq_7.prop", "", true}
				, {"colors.xsts", "currentColor_eq_Green.prop", "", true}
				, {"counter5.xsts", "x_eq_5.prop", "", true}
				, {"forever5.xsts", "x_eq_5.prop", "", true}
				, {"counter6to7.xsts", "x_eq_5.prop", "", false}
		});
	}

	@Test
	public void test() throws IOException {
		if (propFileName.isBlank() && !acceptingLocationName.isBlank())
			testWithCfa();
		if (!propFileName.isBlank() && acceptingLocationName.isBlank())
			testWithXsts();
	}

	private void testWithXsts() throws IOException {
		XSTS xsts;
		try (InputStream inputStream = new SequenceInputStream(new FileInputStream(String.format("src/test/resources/xsts/%s", fileName)), new FileInputStream(String.format("src/test/resources/prop/%s", propFileName)))) {
			xsts = XstsDslManager.createXsts(inputStream);
		}
		final Analysis<XstsState<PredState>, XstsAction, PredPrec> analysis = XstsAnalysis.create(PredAnalysis.create(abstractionSolver, PredAbstractors.booleanSplitAbstractor(abstractionSolver), xsts.getInitFormula()));
		final LTS<XstsState<PredState>, XstsAction> lts = XstsLts.create(xsts, XstsStmtOptimizer.create(DefaultStmtOptimizer.create()));
		final Predicate<XstsState<PredState>> statePredicate = new XstsStatePredicate<>(new ExprStatePredicate(xsts.getProp(), abstractionSolver));
		final AcceptancePredicate<XstsState<PredState>, XstsAction> target = AcceptancePredicate.ofStatePredicate(statePredicate);
		logger.write(Logger.Level.MAINSTEP, "Verifying %s%n", xsts.getProp());
		final LDGCegarVerifier<XstsState<PredState>, XstsAction, PredPrec> verifier = LDGCegarVerifier.of(analysis, lts, target, logger, itpSolver, new ItpRefToPredPrec(ExprSplitters.atoms()));

		final PredPrec precision = PredPrec.of();
		Optional<Trace<XstsState<PredState>, XstsAction>> result = verifier.verify(precision, SearchStrategy.DFS, RefinerStrategy.MILANO);
		Assert.assertEquals(this.result, result.isPresent());
	}

	private void testWithCfa() throws IOException {
		final CFA cfa = CfaDslManager.createCfa(new FileInputStream(String.format("src/test/resources/cfa/%s", fileName)));
		final CfaLts lts = CfaConfigBuilder.Encoding.SBE.getLts(null);
		final Analysis<CfaState<PredState>, CfaAction, CfaPrec<PredPrec>> analysis = CfaAnalysis
				.create(cfa.getInitLoc(), PredAnalysis.create(abstractionSolver, PredAbstractors.booleanSplitAbstractor(abstractionSolver), True()));
		final Predicate<CfaState<PredState>> statePredicate = cfaState -> cfaState.getLoc().getName().equals(acceptingLocationName);
		final AcceptancePredicate<CfaState<PredState>, CfaAction> target = AcceptancePredicate.ofStatePredicate(statePredicate);
		final RefutationToPrec<PredPrec, ItpRefutation> refToPrec = new ItpRefToPredPrec(ExprSplitters.atoms());
		final RefutationToGlobalCfaPrec<PredPrec, ItpRefutation> cfaRefToPrec = new RefutationToGlobalCfaPrec<>(refToPrec, cfa.getInitLoc());
		final LDGCegarVerifier<CfaState<PredState>, CfaAction, CfaPrec<PredPrec>> verifier = LDGCegarVerifier.of(analysis, lts, target, logger, itpSolver, cfaRefToPrec);

		final GlobalCfaPrec<PredPrec> prec = GlobalCfaPrec.create(PredPrec.of());
		Optional<Trace<CfaState<PredState>, CfaAction>> res = verifier.verify(prec, SearchStrategy.DFS, RefinerStrategy.MILANO);
		Assert.assertEquals(result, res.isPresent());
	}
}
