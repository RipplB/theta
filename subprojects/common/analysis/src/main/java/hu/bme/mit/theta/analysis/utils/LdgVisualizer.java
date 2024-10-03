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
package hu.bme.mit.theta.analysis.utils;

import hu.bme.mit.theta.analysis.algorithm.loopchecker.LDGTrace;
import hu.bme.mit.theta.analysis.algorithm.loopchecker.ldg.LDG;
import hu.bme.mit.theta.analysis.algorithm.loopchecker.ldg.LDGEdge;
import hu.bme.mit.theta.analysis.algorithm.loopchecker.ldg.LDGNode;
import hu.bme.mit.theta.analysis.expr.ExprAction;
import hu.bme.mit.theta.analysis.expr.ExprState;
import hu.bme.mit.theta.common.container.Containers;
import hu.bme.mit.theta.common.visualization.EdgeAttributes;
import hu.bme.mit.theta.common.visualization.Graph;
import hu.bme.mit.theta.common.visualization.LineStyle;
import hu.bme.mit.theta.common.visualization.NodeAttributes;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import static hu.bme.mit.theta.common.visualization.Alignment.LEFT;
import static hu.bme.mit.theta.common.visualization.Shape.RECTANGLE;

public final class LdgVisualizer<S extends ExprState, A extends ExprAction> {

	private static final LineStyle SUCC_EDGE_STYLE = LineStyle.NORMAL;
	private static final String LDG_LABEL = "";
	private static final String LDG_ID = "ldg";
	private static final String FONT = "courier";
	private static final String NODE_ID_PREFIX = "node_";
	private static final Color FILL_COLOR = Color.WHITE;
	private static final Color LINE_COLOR = Color.BLACK;
	private static final Color TARGET_COLOR = Color.RED;
	private static final String PHANTOM_INIT_ID = "phantom_init";

	private final Function<S, String> stateToString;
	private final Function<A, String> actionToString;
	
	private Set<LDGNode<? extends S, ? extends A>> traceNodes = Containers.createSet();
	private Set<LDGEdge<? extends S, ? extends A>> traceEdges = Containers.createSet();

	private static class LazyHolderDefault {

		static final LdgVisualizer<ExprState, ExprAction> INSTANCE = new LdgVisualizer<>(Object::toString,
				Object::toString);
	}

	private static class LazyHolderExprStatesOnly {

		static final LdgVisualizer<ExprState, ExprAction> INSTANCE = new LdgVisualizer<>(Object::toString, a -> "");
	}

	private LdgVisualizer(final Function<S, String> stateToString,
						 final Function<A, String> actionToString) {
		this.stateToString = stateToString;
		this.actionToString = actionToString;
	}

	public static <S extends ExprState, A extends ExprAction> LdgVisualizer<S, A> create(
			final Function<S, String> stateToString, final Function<A, String> actionToString) {
		return new LdgVisualizer<>(stateToString, actionToString);
	}

	public static LdgVisualizer<ExprState, ExprAction> getDefault() {
		return LazyHolderDefault.INSTANCE;
	}

	public static LdgVisualizer<ExprState, ExprAction> getExprStatesOnly() {
		return LazyHolderExprStatesOnly.INSTANCE;
	}

	public Graph visualize(final LDG<? extends S, ? extends A> ldg) {
		traceNodes = Containers.createSet();
		traceEdges = Containers.createSet();
		return createVisualization(ldg);
	}

	public Graph visualize(final LDG<? extends S, ? extends A> ldg, final LDGTrace<? extends S, ? extends A> trace) {
		traceEdges = new HashSet<>();
		traceEdges.addAll(trace.getTail());
		traceEdges.addAll(trace.getLoop());
		traceNodes = new HashSet<>();
		trace.getTail().stream().map(LDGEdge::source).forEach(traceNodes::add);
		trace.getLoop().stream().map(LDGEdge::source).forEach(traceNodes::add);
		return createVisualization(ldg);
	}

	private Graph createVisualization(final LDG<? extends S, ? extends A> ldg) {
		final Graph graph = new Graph(LDG_ID, LDG_LABEL);

		final Set<LDGNode<? extends S, ? extends A>> traversed = Containers.createSet();

		for (final LDGNode<? extends S, ? extends A> initNode : ldg.getInitNodes()) {
			traverse(graph, initNode, traversed);
			final NodeAttributes nAttributes = NodeAttributes.builder().label("")
					.fillColor(FILL_COLOR)
					.lineColor(FILL_COLOR).lineStyle(SUCC_EDGE_STYLE).peripheries(1).build();
			graph.addNode(PHANTOM_INIT_ID + initNode.getId(), nAttributes);
			final EdgeAttributes eAttributes = EdgeAttributes.builder().label("")
					.color(traceNodes.contains(initNode) ? TARGET_COLOR : LINE_COLOR)
					.lineStyle(SUCC_EDGE_STYLE).build();
			graph.addEdge(PHANTOM_INIT_ID + initNode.getId(), NODE_ID_PREFIX + initNode.getId(),
					eAttributes);
		}

		return graph;
	}

	private void traverse(final Graph graph, final LDGNode<? extends S, ? extends A> node,
						  final Set<LDGNode<? extends S, ? extends A>> traversed) {
		if (traversed.contains(node)) {
			return;
		} else {
			traversed.add(node);
		}
		final String nodeId = NODE_ID_PREFIX + node.getId();
		final int peripheries = node.isAccepting() ? 2 : 1;

		final NodeAttributes nAttributes = NodeAttributes.builder()
				.label(stateToString.apply(node.getState()))
				.alignment(LEFT).shape(RECTANGLE).font(FONT).fillColor(FILL_COLOR)
				.lineColor(traceNodes.contains(node) ? TARGET_COLOR : LINE_COLOR)
				.lineStyle(SUCC_EDGE_STYLE).peripheries(peripheries).build();

		graph.addNode(nodeId, nAttributes);

		for (final LDGEdge<? extends S, ? extends A> edge : node.getOutEdges()) {
			traverse(graph, edge.target(), traversed);
			final String sourceId = NODE_ID_PREFIX + edge.source().getId();
			final String targetId = NODE_ID_PREFIX + edge.target().getId();
			final EdgeAttributes eAttributes = EdgeAttributes.builder()
					.label(actionToString.apply(edge.action()))
					.alignment(LEFT).font(FONT)
					.color(traceEdges.contains(edge) ? TARGET_COLOR : LINE_COLOR)
					.lineStyle(edge.accepting() ? LineStyle.DASHED : SUCC_EDGE_STYLE).build();
			graph.addEdge(sourceId, targetId, eAttributes);
		}
	}

}
