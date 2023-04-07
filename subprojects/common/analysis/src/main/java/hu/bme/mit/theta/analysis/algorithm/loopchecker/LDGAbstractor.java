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
import hu.bme.mit.theta.analysis.algorithm.loopchecker.ldg.LDG;
import hu.bme.mit.theta.analysis.algorithm.loopchecker.ldg.LDGEdge;
import hu.bme.mit.theta.analysis.algorithm.loopchecker.ldg.LDGNode;
import hu.bme.mit.theta.analysis.expr.ExprAction;
import hu.bme.mit.theta.analysis.expr.ExprState;
import hu.bme.mit.theta.common.logging.Logger;
import hu.bme.mit.theta.common.logging.NullLogger;

import java.util.*;
import java.util.function.Function;
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

	public Optional<LDGTrace<S, A>> onTheFlyCheck(P precision, SearchStrategy strategy) {
		ldg = LDG.create(analysis.getInitFunc().getInitStates(precision), target);
		prec = precision;
		logger.write(Logger.Level.INFO, "On-the-fly checking started from %d initial nodes with strategy %s%n", ldg.getInitNodes().size(), strategy);
		return switch (strategy) {
			case DFS -> dfsSearch();
		};
	}

	private Optional<LDGTrace<S, A>> dfsSearch() {
		for (LDGNode<S, A> initNode : ldg.getInitNodes()) {
			Optional<LDGTrace<S, A>> possibleTrace = expandFromInitNodeUntilTarget(initNode);
			if (possibleTrace.isPresent())
				return possibleTrace;
		}
		return Optional.empty();
	}

	private Optional<LDGTrace<S, A>> expandFromInitNodeUntilTarget(LDGNode<S, A> initNode) {
		return Optional.ofNullable(expandThroughNodeUntilTarget(new LinkedHashMap<>(), new LDGEdge<>(null, initNode, null, false), new LinkedList<>(), 0).lasso);
	}

	private BacktrackResult<S, A> expandThroughNodeUntilTarget(LinkedHashMap<LDGNode<S, A>, Integer> pathSoFar, LDGEdge<S, A> incomingEdge, LinkedList<LDGEdge<S, A>> edgesSoFar, int targetsSoFar) {
		LDGNode<S, A> expandingNode = incomingEdge.target();
		Function<LDGNode<S, A>, Stream<LDGEdge<S, A>>> expandStrategy;
		if (!expandingNode.isExpanded())
			expandStrategy = this::expand;
		else {
			expandStrategy = expandingNode.getValidLoopHondas().stream().filter(pathSoFar::containsKey).anyMatch(node -> pathSoFar.get(node) < targetsSoFar) ? this::traverse : n -> Stream.empty();
		}
		expandingNode.setExpanded(true);
		logger.write(Logger.Level.VERBOSE, "Expanding through %s edge to %s node with state %s%n", incomingEdge.accepting() ? "accepting" : "not accepting", expandingNode.isAccepting() ? "accepting" : "not accepting", expandingNode.getState());
		if (expandingNode.getState().isBottom()) {
			logger.write(Logger.Level.VERBOSE, "Node is a dead end since its bottom%n");
			return new BacktrackResult<>();
		}
		int totalTargets = expandingNode.isAccepting() || incomingEdge.accepting() ? targetsSoFar + 1 : targetsSoFar;
		if (pathSoFar.containsKey(expandingNode) && pathSoFar.get(expandingNode) < totalTargets) {
			logger.write(Logger.Level.RESULT, "Found trace with a length of %d, building lasso...%n", pathSoFar.size());
			logger.write(Logger.Level.DETAIL, "Honda should be: %s", expandingNode.getState());
			pathSoFar.forEach((node, targetsThatFar) -> logger.write(Logger.Level.DETAIL, "Node state %s | targets that far: %d%n", node.getState(), targetsThatFar));
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
		Collection<BacktrackResult<S, A>> results = expandStrategy.apply(expandingNode)
				.map(newEdge -> expandThroughNodeUntilTarget(pathSoFar, newEdge, edgesSoFar, totalTargets))
				.toList();
		Optional<BacktrackResult<S, A>> result = results.stream().filter(res -> res.lasso != null).findAny();
		if (result.isPresent())
			return result.get();
		Collection<LDGNode<S, A>> validLoopHondas = results.stream().map(BacktrackResult::hondas).flatMap(Collection::stream).toList();
		expandingNode.addValidLoopHondas(validLoopHondas);
		pathSoFar.remove(expandingNode);
		edgesSoFar.removeLast();
		return new BacktrackResult<>(validLoopHondas);
	}

	private Stream<LDGEdge<S, A>> expand(LDGNode<S, A> expandingNode) {
		S state = expandingNode.getState();
		return lts.getEnabledActionsFor(state)
				.stream()
				.flatMap(action ->
						createNewNodesAndDrawEdges(expandingNode, state, action)
				);
	}

	private Stream<LDGEdge<S, A>> traverse(LDGNode<S, A> expandingNode) {
		return expandingNode.getOutEdges().stream();
	}

	private Stream<LDGEdge<S, A>> createNewNodesAndDrawEdges(LDGNode<S, A> expandingNode, S state, A action) {
		return analysis
				.getTransFunc()
				.getSuccStates(state, action, prec)
				.stream()
				.map(ldg::getOrCreateNode)
				.map(newNode -> connectTwoNodes(expandingNode, newNode, action));
	}

	public LDG<S, A> getLdg() {
		return ldg;
	}

	record BacktrackResult<S extends ExprState, A extends ExprAction> (Set<LDGNode<S, A>> hondas, LDGTrace<S, A> lasso) {
		BacktrackResult(Collection<LDGNode<S, A>> hondas) {
			this(new HashSet<>(hondas), null);
		}

		BacktrackResult() {
			this(new HashSet<>(0), null);
		}

		BacktrackResult(LDGTrace<S, A> lasso) {
			this(new HashSet<>(0), lasso);
		}

	}

}
