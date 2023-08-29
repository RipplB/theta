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
package hu.bme.mit.theta.cfa.buchi;

import hu.bme.mit.theta.cfa.dsl.gen.LTLGrammarBaseVisitor;
import hu.bme.mit.theta.cfa.dsl.gen.LTLGrammarParser;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.HashMap;

/**
 * Returns whether an AST element represents an LTL expression that has no temporal operators.
 * We need to convert all these into atomic propositions that Spot can interpret.
 * So in the AST, the F G(not err), the largest expression (not err) will be converted to atomic proposition ap0.
 * The resulting LTL expression, which now Spot can interpret, is F G ap0.
 * Whether there is an LTL expression, is returned by LTLExprVisitor.
 * The link is stored in APGeneratorVisitor's result.
 */
public class LTLExprVisitor extends LTLGrammarBaseVisitor<Boolean> {

	private static LTLExprVisitor instance = new LTLExprVisitor();

	private LTLExprVisitor() {
	}

	;

	public static LTLExprVisitor getInstance() {
		return instance;
	}

	HashMap<ParserRuleContext, Boolean> ltl = new HashMap<ParserRuleContext, Boolean>();

	@Override
	public Boolean visitModel(LTLGrammarParser.ModelContext ctx) {
		return super.visitModel(ctx);
	}

	@Override
	public Boolean visitImplyExpression(LTLGrammarParser.ImplyExpressionContext ctx) {
		if (ltl.get(ctx) != null) return ltl.get(ctx);
		for (LTLGrammarParser.OrExprContext op : ctx.ops) {
			if (visitOrExpr(op)) {
				ltl.put(ctx, true);
				return true;
			}
		}
		ltl.put(ctx, false);
		return false;
	}

	@Override
	public Boolean visitAndExpr(LTLGrammarParser.AndExprContext ctx) {
		if (ltl.get(ctx) != null) return ltl.get(ctx);
		for (LTLGrammarParser.NotExprContext op : ctx.ops) {
			if (visitNotExpr(op)) {
				ltl.put(ctx, true);
				return true;
			}
		}
		ltl.put(ctx, false);
		return false;
	}

	@Override
	public Boolean visitNotExpr(LTLGrammarParser.NotExprContext ctx) {
		if (ltl.get(ctx) != null) return ltl.get(ctx);
		for (LTLGrammarParser.NotExprContext op : ctx.ops) {
			if (visitNotExpr(op)) {
				ltl.put(ctx, true);
				return true;
			}
		}
		if (ctx.binaryLtlExpr() != null && visitBinaryLtlExpr(ctx.binaryLtlExpr())) {
			ltl.put(ctx, true);
			return true;
		}
		ltl.put(ctx, false);
		return false;
	}

	@Override
	public Boolean visitBinaryLtlExpr(LTLGrammarParser.BinaryLtlExprContext ctx) {
		if (ltl.get(ctx) != null) return ltl.get(ctx);
		if (ctx.type != null) {
			ltl.put(ctx, true);
			return true;
		}
		boolean child = visitLtlExpr(ctx.ltlExpr());
		ltl.put(ctx, child);
		return child;
	}

	@Override
	public Boolean visitBinaryLtlOp(LTLGrammarParser.BinaryLtlOpContext ctx) {
		return false;
	}

	@Override
	public Boolean visitLtlExpr(LTLGrammarParser.LtlExprContext ctx) {
		if (ltl.get(ctx) != null) return ltl.get(ctx);
		if (ctx.type != null) {
			ltl.put(ctx, true);
			return true;
		}
		boolean child = visitEqExpr(ctx.eqExpr());
		ltl.put(ctx, child);
		return child;
	}

	@Override
	public Boolean visitLtlOp(LTLGrammarParser.LtlOpContext ctx) {
		return false;
	}

	@Override
	public Boolean visitEqExpr(LTLGrammarParser.EqExprContext ctx) {
		if (ltl.get(ctx) != null) return ltl.get(ctx);
		for (LTLGrammarParser.RelationExprContext op : ctx.ops) {
			if (visitRelationExpr(op)) {
				ltl.put(ctx, true);
				return true;
			}
		}
		ltl.put(ctx, false);
		return false;
	}

	@Override
	public Boolean visitEqOperator(LTLGrammarParser.EqOperatorContext ctx) {
		return false;
	}

	@Override
	public Boolean visitRelationExpr(LTLGrammarParser.RelationExprContext ctx) {
		if (ltl.get(ctx) != null) return ltl.get(ctx);
		for (LTLGrammarParser.AdditiveExprContext op : ctx.ops) {
			if (visitAdditiveExpr(op)) {
				ltl.put(ctx, true);
				return true;
			}
		}
		ltl.put(ctx, false);
		return false;
	}

	@Override
	public Boolean visitRelationOperator(LTLGrammarParser.RelationOperatorContext ctx) {
		return false;
	}

	@Override
	public Boolean visitAdditiveExpr(LTLGrammarParser.AdditiveExprContext ctx) {
		if (ltl.get(ctx) != null) return ltl.get(ctx);
		for (LTLGrammarParser.MultiplicativeExprContext op : ctx.ops) {
			if (visitMultiplicativeExpr(op)) {
				ltl.put(ctx, true);
				return true;
			}
		}
		ltl.put(ctx, false);
		return false;
	}

	@Override
	public Boolean visitAdditiveOperator(LTLGrammarParser.AdditiveOperatorContext ctx) {
		return false;
	}

	@Override
	public Boolean visitMultiplicativeExpr(LTLGrammarParser.MultiplicativeExprContext ctx) {
		if (ltl.get(ctx) != null) return ltl.get(ctx);
		for (LTLGrammarParser.NegExprContext op : ctx.ops) {
			if (visitNegExpr(op)) {
				ltl.put(ctx, true);
				return true;
			}
		}
		ltl.put(ctx, false);
		return false;
	}

	@Override
	public Boolean visitMultiplicativeOperator(LTLGrammarParser.MultiplicativeOperatorContext ctx) {
		return false;
	}

	@Override
	public Boolean visitNegExpr(LTLGrammarParser.NegExprContext ctx) {
		if (ltl.get(ctx) != null) return ltl.get(ctx);
		for (LTLGrammarParser.NegExprContext op : ctx.ops) {
			if (visitNegExpr(op)) {
				ltl.put(ctx, true);
				return true;
			}
		}
		if (ctx.primaryExpr() != null && visitPrimaryExpr(ctx.primaryExpr())) {
			ltl.put(ctx, true);
			return true;
		}
		ltl.put(ctx, false);
		return false;
	}

	@Override
	public Boolean visitPrimaryExpr(LTLGrammarParser.PrimaryExprContext ctx) {
		if (ltl.get(ctx) != null) return ltl.get(ctx);
		boolean child = false;
		if (ctx.boolLitExpr() != null) child = visitBoolLitExpr(ctx.boolLitExpr());
		if (ctx.intLitExpr() != null) child = visitIntLitExpr(ctx.intLitExpr());
		if (ctx.parenExpr() != null) child = visitParenExpr(ctx.parenExpr());
		ltl.put(ctx, child);
		return child;
	}

	@Override
	public Boolean visitBoolLitExpr(LTLGrammarParser.BoolLitExprContext ctx) {
		return false;
	}

	@Override
	public Boolean visitParenExpr(LTLGrammarParser.ParenExprContext ctx) {
		if (ltl.get(ctx) != null) return ltl.get(ctx);
		for (LTLGrammarParser.ImplyExpressionContext op : ctx.ops) {
			if (visitImplyExpression(op)) {
				ltl.put(ctx, true);
				return true;
			}
		}
		ltl.put(ctx, false);
		return false;
	}

	@Override
	public Boolean visitIntLitExpr(LTLGrammarParser.IntLitExprContext ctx) {
		return false;
	}

	@Override
	public Boolean visitVariable(LTLGrammarParser.VariableContext ctx) {
		return false;
	}

	@Override
	public Boolean visitOrExpr(LTLGrammarParser.OrExprContext ctx) {
		if (ltl.get(ctx) != null) return ltl.get(ctx);
		for (LTLGrammarParser.AndExprContext op : ctx.ops) {
			if (visitAndExpr(op)) {
				ltl.put(ctx, true);
				return true;
			}
		}
		ltl.put(ctx, false);
		return false;

	}

}
