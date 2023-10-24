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
import hu.bme.mit.theta.analysis.Prec;
import hu.bme.mit.theta.analysis.State;
import hu.bme.mit.theta.analysis.algorithm.loopchecker.ldg.LDG;
import hu.bme.mit.theta.analysis.algorithm.loopchecker.ldg.LDGEdge;
import hu.bme.mit.theta.analysis.algorithm.loopchecker.ldg.LDGNode;
import hu.bme.mit.theta.analysis.expr.ExprAction;
import hu.bme.mit.theta.analysis.expr.ExprState;
import hu.bme.mit.theta.common.container.Containers;
import hu.bme.mit.theta.common.logging.Logger;
import hu.bme.mit.theta.common.logging.NullLogger;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class LDGAbstractor<S extends ExprState, A extends ExprAction, P extends Prec> {

	private final Logger logger;

	final AcceptancePredicate<? super S, ? super A> target;
	final Analysis<S, ? super A, ? super P> analysis;
	final LTS<? super S, ? extends A> lts;

	private LDG<S, A> ldg;
	private P prec;

	private LDGAbstractor(AcceptancePredicate<? super S, ? super A> target, Analysis<S, ? super A, ? super P> analysis, LTS<? super S, ? extends A> lts, Logger logger) {
		this.logger = logger != null ? logger : NullLogger.getInstance();
		this.target = target;
		this.analysis = analysis;
		this.lts = lts;
	}

	public static <S extends ExprState, A extends ExprAction, P extends Prec> LDGAbstractor<S, A, P> create(Analysis<S, ? super A, ? super P> analysis, LTS<? super S, ? extends A> lts, AcceptancePredicate<? super S, ? super A> target, Logger logger) {
		return new LDGAbstractor<>(target, analysis, lts, logger);
	}

	public LDGEdge<S, A> connectTwoNodes(LDGNode<S, A> from, LDGNode<S, A> to, A edgeAction) {
		LDGEdge<S, A> edge = new LDGEdge<>(from, to, edgeAction, target.testAction(edgeAction));
		from.addOutEdge(edge);
		to.addInEdge(edge);
		return edge;
	}

	public Collection<LDGTrace<S, A>> onTheFlyCheck(P precision, SearchStrategy strategy) {
		ldg = LDG.create(analysis.getInitFunc().getInitStates(precision), target);
		prec = precision;
		logger.write(Logger.Level.INFO, "On-the-fly checking started from %d initial nodes with strategy %s%n", ldg.getInitNodes().size(), strategy);
		return switch (strategy) {
			case DFS -> dfsSearch();
			case NDFS -> ndfs();
			case FULL -> fullSearch();
		};
	}

	private Collection<LDGTrace<S, A>> dfsSearch() {
		for (LDGNode<S, A> initNode : ldg.getInitNodes()) {
			Collection<LDGTrace<S, A>> possibleTraces = expandFromInitNodeUntilTarget(initNode, true);
			if (!possibleTraces.isEmpty()) {
				return possibleTraces;
			}
		}
		return Collections.emptyList();
	}

	private Collection<LDGTrace<S, A>> ndfs() {
		for (var initNode :
				ldg.getInitNodes()) {
			for (var edge :
					expand(initNode)) {
				var result = blueSearch(edge, new LinkedList<>(), Containers.createSet(), Containers.createSet());
				if (!result.isEmpty())
					return result;
			}
		}
		return Collections.emptyList();
	}

	private List<LDGEdge<S, A>> redSearch(LDGNode<S, A> seed, LDGEdge<S, A> edge, LinkedList<LDGEdge<S, A>> trace, Set<LDGNode<S, A>> redNodes) {
		var targetNode = edge.target();
		trace.add(edge);
		if (targetNode.equals(seed)) {
			return trace;
		}
		if (redNodes.contains(targetNode)) {
			trace.removeLast();
			return Collections.emptyList();
		}
		redNodes.add(edge.target());
		for (var nextEdge :
				expand(targetNode)) {
			var redSearch = redSearch(seed, nextEdge, trace, redNodes);
			if (!redSearch.isEmpty())
				return redSearch;
		}
		trace.removeLast();
		return Collections.emptyList();
	}

	private Collection<LDGTrace<S, A>> blueSearch(LDGEdge<S, A> edge, LinkedList<LDGEdge<S, A>> trace, Collection<LDGNode<S, A>> blueNodes, Set<LDGNode<S, A>> redNodes) {
		var targetNode = edge.target();
		trace.add(edge);
		if (target.test(targetNode.getState(), edge.action())) {
			var accNode = targetNode.isAccepting() ? targetNode : edge.source();
			List<LDGEdge<S, A>> redSearch = redSearch(accNode, edge, new LinkedList<>(trace), Containers.createSet());
			if (!redSearch.isEmpty())
				return Collections.singleton(LDGTrace.lassoFromEdgesWithHonda(redSearch, accNode));
		}
		if (blueNodes.contains(targetNode)) {
			trace.removeLast();
			return Collections.emptyList();
		}
		blueNodes.add(edge.target());
		for (var nextEdge :
				expand(targetNode)) {
			var blueSearch = blueSearch(nextEdge, trace, blueNodes, redNodes);
			if (!blueSearch.isEmpty())
				return blueSearch;
		}
		trace.removeLast();
		return Collections.emptyList();
	}

	private Collection<LDGTrace<S, A>> fullSearch() {
		return ldg.getInitNodes().stream().map(initNode -> expandFromInitNodeUntilTarget(initNode, false)).flatMap(Collection::stream).toList();
	}

	private Collection<LDGTrace<S, A>> expandFromInitNodeUntilTarget(LDGNode<S, A> initNode, boolean stopAtLasso) {
		return expandThroughNode(new LinkedHashMap<>(), new LDGEdge<>(null, initNode, null, false), new LinkedList<>(), 0, stopAtLasso).lassos;
	}

	private BacktrackResult<S, A> expandThroughNode(LinkedHashMap<LDGNode<S, A>, Integer> pathSoFar, LDGEdge<S, A> incomingEdge, LinkedList<LDGEdge<S, A>> edgesSoFar, int targetsSoFar, boolean stopAtLasso) {
		LDGNode<S, A> expandingNode = incomingEdge.target();
		logger.write(Logger.Level.VERBOSE, "Expanding through %s edge to %s node with state %s%n", incomingEdge.accepting() ? "accepting" : "not accepting", expandingNode.isAccepting() ? "accepting" : "not accepting", expandingNode.getState());
		if (expandingNode.getState().isBottom()) {
			logger.write(Logger.Level.VERBOSE, "Node is a dead end since its bottom%n");
			return new BacktrackResult<>();
		}
		int totalTargets = expandingNode.isAccepting() || incomingEdge.accepting() ? targetsSoFar + 1 : targetsSoFar;
		if (pathSoFar.containsKey(expandingNode) && pathSoFar.get(expandingNode) < totalTargets) {
			logger.write(Logger.Level.SUBSTEP, "Found trace with a length of %d, building lasso...%n", pathSoFar.size());
			logger.write(Logger.Level.DETAIL, "Honda should be: %s", expandingNode.getState());
			pathSoFar.forEach((node, targetsThatFar) -> logger.write(Logger.Level.VERBOSE, "Node state %s | targets that far: %d%n", node.getState(), targetsThatFar));
			edgesSoFar.add(incomingEdge);
			LDGTrace<S, A> lasso = LDGTrace.lassoFromEdgesWithHonda(edgesSoFar, expandingNode);
			logger.write(Logger.Level.DETAIL, "Built the following lasso:%n");
			lasso.print(logger, Logger.Level.DETAIL);
			return new BacktrackResult<>(lasso);
		}
		if (pathSoFar.containsKey(expandingNode)) {
			logger.write(Logger.Level.VERBOSE, "Reached loop but no acceptance inside%n");
			return new BacktrackResult<>(Collections.singleton(expandingNode));
		}
		edgesSoFar.add(incomingEdge);
		pathSoFar.put(expandingNode, totalTargets);
		boolean needsTraversing = !expandingNode.isExpanded() || expandingNode
				.getValidLoopHondas()
				.stream()
				.filter(pathSoFar::containsKey)
				.anyMatch(node -> pathSoFar.get(node) < targetsSoFar);
		Function<LDGNode<S, A>, Collection<LDGEdge<S, A>>> expandStrategy = needsTraversing
				? this::expand
				: n -> Collections.emptySet();
		Collection<LDGEdge<S, A>> outgoingEdges = expandStrategy.apply(expandingNode);
		List<BacktrackResult<S, A>> results = new ArrayList<>();
		for (LDGEdge<S, A> newEdge :
				outgoingEdges) {
			BacktrackResult<S, A> result = expandThroughNode(pathSoFar, newEdge, edgesSoFar, totalTargets, stopAtLasso);
			results.add(result);
			if (stopAtLasso && !result.lassos.isEmpty())
				break;
		}
		BacktrackResult<S, A> result = combineLassos(results);
		if (!result.lassos.isEmpty())
			return result;
		Collection<LDGNode<S, A>> validLoopHondas = results.stream().map(BacktrackResult::hondas).flatMap(Collection::stream).toList();
		expandingNode.addValidLoopHondas(validLoopHondas);
		pathSoFar.remove(expandingNode);
		edgesSoFar.removeLast();
		return new BacktrackResult<>(validLoopHondas);
	}

	private Collection<LDGEdge<S, A>> expand(LDGNode<S, A> expandingNode) {
		if (expandingNode.isExpanded())
			return expandingNode.getOutEdges();
		expandingNode.setExpanded();
		S state = expandingNode.getState();
		return lts.getEnabledActionsFor(state)
				.stream()
				.flatMap(action ->
						createNewNodesAndDrawEdges(expandingNode, state, action)
				)
				.toList();
	}

	private Stream<LDGEdge<S, A>> createNewNodesAndDrawEdges(LDGNode<S, A> expandingNode, S state, A action) {
		return analysis
				.getTransFunc()
				.getSuccStates(state, action, prec)
				.stream()
				.filter(Predicate.not(State::isBottom))
				.map(ldg::getOrCreateNode)
				.map(newNode -> connectTwoNodes(expandingNode, newNode, action));
	}

	public LDG<S, A> getLdg() {
		return ldg;
	}

	record BacktrackResult<S extends ExprState, A extends ExprAction> (Set<LDGNode<S, A>> hondas, List<LDGTrace<S, A>> lassos) {
		BacktrackResult(Collection<LDGNode<S, A>> hondas) {
			this(new HashSet<>(hondas), Collections.emptyList());
		}

		BacktrackResult() {
			this(Collections.emptySet(), Collections.emptyList());
		}

		BacktrackResult(LDGTrace<S, A> lasso) {
			this(Collections.emptySet(), Collections.singletonList(lasso));
		}

		BacktrackResult(List<LDGTrace<S, A>> lassos) {
			this(Collections.emptySet(), lassos);
		}

	}

	private BacktrackResult<S, A> combineLassos(Collection<BacktrackResult<S, A>> results) {
		List<LDGTrace<S, A>> lassos = new ArrayList<>();
		results.forEach(result -> lassos.addAll(result.lassos));
		return new BacktrackResult<>(lassos);
	}

}
