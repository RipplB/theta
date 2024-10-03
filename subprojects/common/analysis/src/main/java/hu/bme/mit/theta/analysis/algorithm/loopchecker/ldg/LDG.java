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

import hu.bme.mit.theta.analysis.algorithm.loopchecker.AcceptancePredicate;
import hu.bme.mit.theta.analysis.expr.ExprAction;
import hu.bme.mit.theta.analysis.expr.ExprState;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class LDG<S extends ExprState, A extends ExprAction> {

	private final Collection<? extends S> initStates;
	private final Map<S, LDGNode<S, A>> nodes;
	private final AcceptancePredicate<? super S, ? super A> acceptancePredicate;

	LDG(final Collection<? extends S> initStates, final AcceptancePredicate<? super S, ? super A> acceptancePredicate) {
		this.acceptancePredicate = acceptancePredicate;
		this.initStates = initStates;
		nodes = new HashMap<>();
		initStates.forEach(this::getOrCreateNode);
	}

	public static <S extends ExprState, A extends ExprAction> LDG<S, A> create(
			final Collection<? extends S> initStates, final AcceptancePredicate<? super S, ? super A> acceptancePredicate
			) {
		return new LDG<>(initStates, acceptancePredicate);
	}

	public LDGNode<S, A> getOrCreateNode(S state) {
		return nodes.computeIfAbsent(state, s -> LDGNode.of(state, acceptancePredicate.testState(state)));
	}

	public Collection<LDGNode<S, A>> getInitNodes() {
		return initStates.stream().map(nodes::get).toList();
	}

	public boolean containsNode(LDGNode<S, A> node) {
		return nodes.containsKey(node.getState());
	}

}
