/*
 *  Copyright 2024 Budapest University of Technology and Economics
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

package hu.bme.mit.theta.analysis.algorithm.loopchecker.abstraction

import hu.bme.mit.theta.analysis.algorithm.loopchecker.AcceptancePredicate
import hu.bme.mit.theta.analysis.algorithm.loopchecker.LDGTrace
import hu.bme.mit.theta.analysis.algorithm.loopchecker.ldg.LDGEdge
import hu.bme.mit.theta.analysis.algorithm.loopchecker.ldg.LDGNode
import hu.bme.mit.theta.analysis.expr.ExprAction
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.common.logging.Logger
import java.util.*


object NdfsSearchStrategy : ILoopcheckerSearchStrategy {

    override fun <S : ExprState, A : ExprAction> search(
        initNodes: Collection<LDGNode<S, A>>, target: AcceptancePredicate<S, A>, expand: NodeExpander<S, A>,
        logger: Logger
    ): Collection<LDGTrace<S, A>> {
        for (node in initNodes) {
            for (edge in expand(node)) {
                val result = blueSearch(edge, mutableListOf(), mutableSetOf(), mutableSetOf(), target, expand)
                if (!result.isEmpty()) return result
            }
        }
        return emptyList()
    }

    private fun <S : ExprState, A : ExprAction> redSearch(
        seed: LDGNode<S, A>, edge: LDGEdge<S, A>, trace: MutableList<LDGEdge<S, A>>,
        redNodes: MutableSet<LDGNode<S, A>>, target: AcceptancePredicate<S, A>, expand: NodeExpander<S, A>
    ): List<LDGEdge<S, A>> {
        val targetNode = edge.target
        trace.add(edge)
        if (targetNode == seed) {
            return trace
        }
        if (redNodes.contains(targetNode)) {
            trace.removeLast()
            return emptyList()
        }
        redNodes.add(edge.target)
        for (nextEdge in expand(targetNode)) {
            val redSearch: List<LDGEdge<S, A>> = redSearch(seed, nextEdge, trace, redNodes, target, expand)
            if (redSearch.isNotEmpty()) return redSearch
        }
        trace.removeLast()
        return emptyList()
    }

    private fun <S : ExprState, A : ExprAction> blueSearch(
        edge: LDGEdge<S, A>, trace: MutableList<LDGEdge<S, A>>, blueNodes: MutableCollection<LDGNode<S, A>>,
        redNodes: Set<LDGNode<S, A>>, target: AcceptancePredicate<S, A>, expand: NodeExpander<S, A>
    ): Collection<LDGTrace<S, A>> {
        val targetNode = edge.target
        trace.add(edge)
        if (target.test(Pair(targetNode.state, edge.action))) {
            // Edge source can only be null artificially, and is only used when calling other search strategies
            val accNode =
                if (targetNode.accepting) targetNode else edge.source!!
            val redSearch: List<LDGEdge<S, A>> =
                redSearch(accNode, edge, LinkedList<LDGEdge<S, A>>(trace), mutableSetOf(), target, expand)
            if (redSearch.isNotEmpty()) return setOf(LDGTrace(redSearch, accNode))
        }
        if (blueNodes.contains(targetNode)) {
            trace.removeLast()
            return emptyList()
        }
        blueNodes.add(edge.target)
        for (nextEdge in expand(targetNode)) {
            val blueSearch: Collection<LDGTrace<S, A>> =
                blueSearch(nextEdge, trace, blueNodes, redNodes, target, expand)
            if (!blueSearch.isEmpty()) return blueSearch
        }
        trace.removeLast()
        return emptyList()
    }

}