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
import hu.bme.mit.theta.analysis.algorithm.loopchecker.refinement.BoundedUnrollingLDGTraceCheckerStrategy
import hu.bme.mit.theta.analysis.expl.ExplState
import hu.bme.mit.theta.analysis.expr.ExprAction
import hu.bme.mit.theta.analysis.expr.ExprState
import hu.bme.mit.theta.analysis.expr.StmtAction
import hu.bme.mit.theta.common.logging.NullLogger
import hu.bme.mit.theta.core.decl.Decls
import hu.bme.mit.theta.core.stmt.*
import hu.bme.mit.theta.core.type.booltype.BoolExprs.True
import hu.bme.mit.theta.core.type.booltype.BoolType
import hu.bme.mit.theta.core.type.inttype.IntExprs
import hu.bme.mit.theta.core.type.inttype.IntType
import hu.bme.mit.theta.core.utils.ExprUtils
import hu.bme.mit.theta.solver.z3legacy.Z3LegacySolverFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class VarCollectorTest {

    class MockAction(val stmtz: MutableList<Stmt>) : StmtAction() {

        override fun getStmts() = stmtz
    }

    private val vars = ('a' until 'z').associateWith { Decls.Var(it.toString(), BoolType.getInstance()) }
    private val i = Decls.Var("index", IntType.getInstance())
    private val stmts = mutableListOf(
        SkipStmt.getInstance(),
        AssumeStmt.of(True()),
        SequenceStmt.of(
            listOf(
                AssignStmt.of(vars['c'], vars['b']!!.ref),
                AssignStmt.of(vars['d'], vars['b']!!.ref),
            )
        ),
        NonDetStmt.of(
            listOf(
                IfStmt.of(True(), AssignStmt.of(vars['x'], vars['y']!!.ref), AssignStmt.of(vars['e'], vars['a']!!.ref)),
            )
        ),
        AssignStmt.of(vars['f'], vars['d']!!.ref),
        NonDetStmt.of(listOf(AssignStmt.of(vars['w'], vars['x']!!.ref))),
        AssignStmt.of(vars['a'], vars['a']!!.ref),
        HavocStmt.of(vars['b']),
    )

    private val node = mock<LDGNode<ExprState, ExprAction>>()
    private val edge = mock<LDGEdge<ExprState, ExprAction>>()
    private val action = MockAction(stmts)
    private val trace = mock<LDGTrace<ExprState, ExprAction>>()
    private val solverFactory = Z3LegacySolverFactory.getInstance()

    init {
        whenever(node.state).doReturn(ExplState.bottom())
        whenever(edge.source).doReturn(node)
        whenever(edge.action).doReturn(action)
        whenever(trace.edges).doReturn(listOf(edge))
        whenever(trace.loop).doReturn(listOf(edge))
    }

    @Test
    fun testVarCollection() {
        val buStrat =
            BoundedUnrollingLDGTraceCheckerStrategy(trace, solverFactory, True(), 100, NullLogger.getInstance())
        val result = buStrat.expandUsedVariables(emptySet())
        assertEquals(('a' until 'g').map { vars[it] }.toSet(), result)
    }

}