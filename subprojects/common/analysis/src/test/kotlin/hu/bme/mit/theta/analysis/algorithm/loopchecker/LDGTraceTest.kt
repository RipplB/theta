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

package hu.bme.mit.theta.analysis.algorithm.loopchecker

import hu.bme.mit.theta.analysis.algorithm.loopchecker.ldg.LDGEdge
import hu.bme.mit.theta.analysis.algorithm.loopchecker.ldg.LDGNode
import hu.bme.mit.theta.analysis.expr.ExprAction
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.common.logging.ConsoleLogger
import hu.bme.mit.theta.common.logging.Logger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class LDGTraceTest {

    @Test
    fun testSimpleLasso() {
        val initNode: LDGNode<ExprState, ExprAction> = LDGNode(mock(ExprState::class.java), false)
        val hondaNode: LDGNode<ExprState, ExprAction> = LDGNode(mock(ExprState::class.java), true)
        val loopNode: LDGNode<ExprState, ExprAction> = LDGNode(mock(ExprState::class.java), false)
        assertNotEquals(initNode, hondaNode)
        assertNotEquals(initNode, loopNode)
        assertNotEquals(loopNode, hondaNode)
        val firstEdge = LDGEdge(initNode, hondaNode, mock(ExprAction::class.java), false)
        val secondEdge = LDGEdge(hondaNode, loopNode, mock(ExprAction::class.java), false)
        val thirdEdge = LDGEdge(loopNode, hondaNode, mock(ExprAction::class.java), false)
        initNode.addOutEdge(firstEdge)
        hondaNode.addInEdge(firstEdge)
        hondaNode.addOutEdge(secondEdge)
        loopNode.addInEdge(secondEdge)
        loopNode.addOutEdge(thirdEdge)
        hondaNode.addInEdge(thirdEdge)

        val trace: LDGTrace<ExprState, ExprAction> = LDGTrace(
            listOf(firstEdge, secondEdge, thirdEdge), hondaNode
        )
        trace.print(ConsoleLogger(Logger.Level.INFO), Logger.Level.INFO)

        assertEquals(1, trace.tail.size)
        assertEquals(2, trace.loop.size)
        assertEquals(initNode, trace.tail[0].source)
        assertEquals(hondaNode, trace.tail[0].target)
        assertEquals(hondaNode, trace.loop[0].source)
        assertEquals(loopNode, trace.loop[0].target)
        assertEquals(loopNode, trace.loop[1].source)
        assertEquals(hondaNode, trace.loop[1].target)
        assertThrows<IllegalStateException> { trace.getEdge(-1) }
        assertThrows<IllegalStateException> { trace.getEdge(3) }
        assertEquals(firstEdge, trace.getEdge(0))
        assertEquals(secondEdge, trace.getEdge(1))
        assertEquals(thirdEdge, trace.getEdge(2))
        assertEquals(trace.getState(1), trace.getState(3))

        val argTrace = trace.toTrace()
        assertEquals(3, argTrace.length())

    }
}