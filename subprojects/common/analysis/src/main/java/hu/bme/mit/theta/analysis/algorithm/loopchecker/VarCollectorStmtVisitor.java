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

package hu.bme.mit.theta.analysis.algorithm.loopchecker;

import hu.bme.mit.theta.core.decl.VarDecl;
import hu.bme.mit.theta.core.stmt.*;
import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.core.type.Type;
import hu.bme.mit.theta.core.type.anytype.RefExpr;
import hu.bme.mit.theta.core.utils.ExprUtils;

import java.util.Collection;
import java.util.Set;

public final class VarCollectorStmtVisitor implements StmtVisitor<Set<VarDecl<?>>, Void> {

	public static Void visitAll(Collection<Stmt> stmts, Set<VarDecl<?>> vars) {
		final VarCollectorStmtVisitor visitor = new VarCollectorStmtVisitor();
		stmts.forEach(stmt -> stmt.accept(visitor, vars));
		return null;
	}

	private static void findAllRefs(Expr<?> expr, Set<RefExpr<?>> refs) {
		if (expr instanceof RefExpr<?> ref)
			refs.add(ref);
		expr.getOps().forEach(e -> findAllRefs(e, refs));
	}

	@Override
	public Void visit(SkipStmt stmt, Set<VarDecl<?>> param) {
		return null;
	}

	@Override
	public Void visit(AssumeStmt stmt, Set<VarDecl<?>> param) {
		return null;
	}

	@Override
	public <DeclType extends Type> Void visit(AssignStmt<DeclType> stmt, Set<VarDecl<?>> param) {
		Collection<VarDecl<?>> rightVars = ExprUtils.getVars(stmt.getExpr());
		VarDecl<DeclType> leftVar = stmt.getVarDecl();
		for (var rightVar :
				rightVars) {
			if (rightVar.equals(leftVar) || param.contains(rightVar)) {
					param.add(leftVar);
					return null;
			}
		}
		return null;
	}

	@Override
	public <PtrType extends Type, OffsetType extends Type, DeclType extends Type> Void visit(MemoryAssignStmt<PtrType, OffsetType, DeclType> stmt, Set<VarDecl<?>> param) {
		return null;
	}

	@Override
	public <DeclType extends Type> Void visit(HavocStmt<DeclType> stmt, Set<VarDecl<?>> param) {
//		if (stmt.getVarDecl().getType().getDomainSize().isInfinite())
//			throw new UnsupportedOperationException();
		param.add(stmt.getVarDecl());
		return null;
	}

	@Override
	public Void visit(SequenceStmt stmt, Set<VarDecl<?>> param) {
		return visitAll(stmt.getStmts(), param);
	}

	@Override
	public Void visit(NonDetStmt stmt, Set<VarDecl<?>> param) {
		visitAll(stmt.getStmts(), param);
		return null;
	}

	@Override
	public Void visit(OrtStmt stmt, Set<VarDecl<?>> param) {
		visitAll(stmt.getStmts(), param);
		return null;
	}

	@Override
	public Void visit(LoopStmt stmt, Set<VarDecl<?>> param) {
		stmt.getStmt().accept(this, param);
		return null;
	}

	@Override
	public Void visit(IfStmt stmt, Set<VarDecl<?>> param) {
		stmt.getThen().accept(this, param);
		stmt.getElze().accept(this, param);
		return null;
	}
}
