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

import hu.bme.mit.theta.analysis.Trace;
import hu.bme.mit.theta.analysis.algorithm.loopchecker.exception.InvalidPathException;
import hu.bme.mit.theta.analysis.algorithm.loopchecker.ldg.LDGEdge;
import hu.bme.mit.theta.analysis.algorithm.loopchecker.ldg.LDGNode;
import hu.bme.mit.theta.analysis.expr.ExprAction;
import hu.bme.mit.theta.analysis.expr.ExprState;
import hu.bme.mit.theta.common.logging.Logger;

import java.util.*;
import java.util.function.Predicate;

/**
 * A trace representing a lasso-like counterexample. No object can be created that is not a valid lasso.
 */
public final class LDGTrace<S extends ExprState, A extends ExprAction> {
	private final LinkedList<LDGEdge<S, A>> tail;

	/**
	 * The loop of the lasso is essentially created by something called honda. It can be a knot, simply a metal ring, or many different things.
	 *
	 * @see <a href="https://en.wikipedia.org/wiki/Lasso">The wikipedia page for Lasso</a>
	 */
	private Optional<LDGNode<S, A>> honda;
	private final LinkedList<LDGEdge<S, A>> loop;

	private LDGTrace() {
		tail = new LinkedList<>();
		honda = Optional.empty();
		loop = new LinkedList<>();
	}

	public static <S extends ExprState, A extends ExprAction> LDGTrace<S, A> lassoFromIteratorWithHonda(Iterator<LDGNode<S, A>> iterator, LDGNode<S, A> honda) {
		if (!iterator.hasNext()) {
			throw new InvalidPathException();
		}
		LDGTrace<S, A> trace = new LDGTrace<>();
		LDGNode<S, A> initNode = iterator.next();
		if (initNode.equals(honda)) {
			trace.honda = Optional.of(honda);
			if (!iterator.hasNext()) {
				// single element lasso. Find an edge that goes from and to the node
				Optional<LDGEdge<S, A>> loopEdge = honda.smallerEdgeCollection().stream().filter(edge -> edge.source().equals(edge.target())).findAny();
				trace.loop.add(loopEdge.orElseThrow());
				return trace;
			}
		}
		trace.connectNodesIntoCollection(initNode, iterator, trace.tail, honda::equals);
		if (!iterator.hasNext()) {
			throw new InvalidPathException();
		}
		trace.honda = Optional.of(honda);
		trace.connectNodesIntoCollection(honda, iterator, trace.loop, n -> false);
		trace.loop.add(trace.loop.getLast().target().getOutEdges().stream().filter(edge -> edge.target().equals(honda)).findAny().orElseThrow());
		return trace;
	}

	public static <S extends ExprState, A extends ExprAction> LDGTrace<S, A> lassoFromEdgesWithHonda(List<LDGEdge<S, A>> edges, LDGNode<S, A> honda) {
		if (edges.isEmpty())
			throw new InvalidPathException();
		if (edges.get(0).source() == null)
			edges.remove(0);
		LDGTrace<S, A> trace = new LDGTrace<>();
		Collection<LDGEdge<S, A>> group = trace.tail;
		for (LDGEdge<S, A> edge :
				edges) {
			if (edge.source().equals(honda)) {
				group = trace.loop;
				trace.honda = Optional.of(honda);
			}
			group.add(edge);
		}
		return trace;
	}

	public void print(Logger logger, Logger.Level level) {
		tail.forEach(edge -> logger.write(level, "%s%n---through action:---%n%s%n--------->%n", edge.source().getState(), edge.action()));
		honda.ifPresent(hondaNode -> logger.write(level, "---HONDA:---%n{ %s }---------%n", hondaNode.getState()));
		loop.forEach(edge -> logger.write(level, "---through action:---%n%s%n--------->%n%s%n", edge.action(), edge.target().getState()));
	}

	private void connectNodesIntoCollection(LDGNode<S, A> initialNode, Iterator<LDGNode<S, A>> iterator, Collection<LDGEdge<S, A>> edgeCollection, Predicate<LDGNode<S, A>> extraStopCondition) {
		for (LDGNode<S, A> fromNode = initialNode, toNode = iterator.next();
				;
			 fromNode = toNode, toNode = iterator.next()) {
			LDGNode<S, A> finalToNode = toNode;
			Optional<LDGEdge<S, A>> edge = fromNode.getOutEdges().stream().filter(outEdge -> outEdge.target().equals(finalToNode)).findAny();
			edgeCollection.add(edge.orElseThrow());
			if (!iterator.hasNext() || extraStopCondition.test(toNode)) {
				return;
			}
		}
	}

	public Trace<S, A> toTrace() {
		List<LDGEdge<S, A>> edges = new ArrayList<>(tail.size() + loop.size());
		edges.addAll(tail);
		edges.addAll(loop);
		List<S> states = new ArrayList<>(edges.size() + 1);
		states.add(tail.getFirst().source().getState());
		edges.stream()
				.map(LDGEdge::target)
				.map(LDGNode::getState)
				.forEach(states::add);
		List<A> actions = edges.stream().map(LDGEdge::action).toList();
		return Trace.of(states, actions);
	}

	public List<LDGEdge<S, A>> getTail() {
		return tail;
	}

	public Optional<LDGNode<S, A>> getHonda() {
		return honda;
	}

	public List<LDGEdge<S, A>> getLoop() {
		return loop;
	}
}
