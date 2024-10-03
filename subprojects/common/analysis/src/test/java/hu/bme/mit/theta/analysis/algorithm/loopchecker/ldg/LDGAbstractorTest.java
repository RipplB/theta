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
package hu.bme.mit.theta.analysis.algorithm.loopchecker.ldg;

import hu.bme.mit.theta.analysis.Analysis;
import hu.bme.mit.theta.analysis.LTS;
import hu.bme.mit.theta.analysis.Prec;
import hu.bme.mit.theta.analysis.algorithm.loopchecker.AcceptancePredicate;
import hu.bme.mit.theta.analysis.algorithm.loopchecker.LDGAbstractor;
import hu.bme.mit.theta.analysis.expr.ExprAction;
import hu.bme.mit.theta.analysis.expr.ExprState;
import hu.bme.mit.theta.common.logging.NullLogger;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class LDGAbstractorTest {

	@Mock
	public ExprState fromState;
	@Mock
	public ExprState toState;
	@Mock
	public ExprAction actionFromTo;

	@Test
	@SuppressWarnings("unchecked")
	public void testConnectTwoNodes() {
		LDGNode<ExprState, ExprAction> from = LDGNode.of(fromState, true);
		LDGNode<ExprState, ExprAction> to = LDGNode.of(toState, true);
		LDGAbstractor<ExprState, ExprAction, ?> abstractor = LDGAbstractor.create((Analysis<ExprState, ExprAction, Prec>) mock(Analysis.class), (LTS<ExprState, ExprAction>) mock(LTS.class), AcceptancePredicate.alwaysTrue(), NullLogger.getInstance());

		LDGEdge<ExprState, ExprAction> edge = abstractor.connectTwoNodes(from, to, actionFromTo);

		Assert.assertEquals(from, edge.source());
		Assert.assertEquals(to, edge.target());
		Assert.assertEquals(1, from.getOutEdges().size());
		Assert.assertEquals(1, to.getInEdges().size());
		Assert.assertTrue(from.getOutEdges().contains(edge));
		Assert.assertTrue(to.getInEdges().contains(edge));
	}
}
