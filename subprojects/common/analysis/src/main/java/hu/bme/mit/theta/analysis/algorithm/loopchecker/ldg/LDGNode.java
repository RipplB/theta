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

import com.google.common.base.Objects;
import hu.bme.mit.theta.analysis.expr.ExprAction;
import hu.bme.mit.theta.analysis.expr.ExprState;

import java.util.*;


public final class LDGNode<S extends ExprState, A extends ExprAction> {
	private static long idCounter = 0;
	private final S state;
	private final Collection<LDGEdge<S, A>> inEdges;
	private final Collection<LDGEdge<S, A>> outEdges;


	private final boolean accepting;
	private final long id;
	private boolean expanded = false;
	private Set<LDGNode<S, A>> validLoopHondas;

	private LDGNode(final S state, boolean accepting) {
		this.state = state;
		this.accepting = accepting;
		inEdges = new ArrayList<>();
		outEdges = new ArrayList<>();
		validLoopHondas = new HashSet<>();
		id = idCounter++;
	}

	static <S extends ExprState, A extends ExprAction> LDGNode<S, A> of(S state, boolean accepting) {
		return new LDGNode<>(state, accepting);
	}

	public boolean isAccepting() {
		return accepting;
	}

	public S getState() {
		return state;
	}

	public void addInEdge(LDGEdge<S, A> edge) {
		inEdges.add(edge);
	}

	public void addOutEdge(LDGEdge<S, A> edge) {
		outEdges.add(edge);
	}

	public Collection<LDGEdge<S, A>> getInEdges() {
		return inEdges;
	}

	public Collection<LDGEdge<S, A>> getOutEdges() {
		return outEdges;
	}

	public Collection<LDGEdge<S, A>> smallerEdgeCollection() {
		return outEdges.size() < inEdges.size() ? getOutEdges() : getInEdges();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		LDGNode<?, ?> ldgNode = (LDGNode<?, ?>) o;
		return Objects.equal(state, ldgNode.state);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(state);
	}

	@Override
	public String toString() {
		return "LDGNode{" +
				"state=" + state +
				", accepting=" + accepting +
				'}';
	}

	public long getId() {
		return id;
	}

	public boolean isExpanded() {
		return expanded;
	}

	public void setExpanded(boolean expanded) {
		this.expanded = expanded;
	}

	public Set<LDGNode<S, A>> getValidLoopHondas() {
		return validLoopHondas;
	}

	public void setValidLoopHondas(Set<LDGNode<S, A>> validLoopHondas) {
		this.validLoopHondas = validLoopHondas;
	}

	public void addValidLoopHondas(Collection<LDGNode<S, A>> hondas) {
		validLoopHondas.addAll(hondas);
	}

}
