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

import hu.bme.mit.theta.analysis.algorithm.loopchecker.LDGTrace;
import hu.bme.mit.theta.analysis.expr.ExprAction;
import hu.bme.mit.theta.analysis.expr.ExprState;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;

import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class LDGTraceTest {

	@Test
	public void testSimpleLasso() {
		LDGNode<ExprState, ExprAction> initNode = LDGNode.of(mock(ExprState.class), false);
		LDGNode<ExprState, ExprAction> hondaNode = LDGNode.of(mock(ExprState.class), true);
		LDGNode<ExprState, ExprAction> loopNode = LDGNode.of(mock(ExprState.class), false);
		Assert.assertNotEquals(initNode, hondaNode);
		Assert.assertNotEquals(initNode, loopNode);
		Assert.assertNotEquals(loopNode, hondaNode);
		LDGEdge<ExprState, ExprAction> firstEdge = new LDGEdge<>(initNode, hondaNode, mock(ExprAction.class), false);
		LDGEdge<ExprState, ExprAction> secondEdge = new LDGEdge<>(hondaNode, loopNode, mock(ExprAction.class), false);
		LDGEdge<ExprState, ExprAction> thirdEdge = new LDGEdge<>(loopNode, hondaNode, mock(ExprAction.class), false);
		initNode.addOutEdge(firstEdge);
		hondaNode.addInEdge(firstEdge);
		hondaNode.addOutEdge(secondEdge);
		loopNode.addInEdge(secondEdge);
		loopNode.addOutEdge(thirdEdge);
		hondaNode.addInEdge(thirdEdge);

		LDGTrace<ExprState, ExprAction> trace = LDGTrace.lassoFromIteratorWithHonda(List.of(initNode, hondaNode, loopNode).iterator(), hondaNode);

		Assert.assertEquals(1, trace.getTail().size());
		Assert.assertEquals(2, trace.getLoop().size());
		Assert.assertEquals(initNode, trace.getTail().get(0).source());
		Assert.assertEquals(hondaNode, trace.getTail().get(0).target());
		Assert.assertEquals(hondaNode, trace.getLoop().get(0).source());
		Assert.assertEquals(loopNode, trace.getLoop().get(0).target());
		Assert.assertEquals(loopNode, trace.getLoop().get(1).source());
		Assert.assertEquals(hondaNode, trace.getLoop().get(1).target());
	}

}
