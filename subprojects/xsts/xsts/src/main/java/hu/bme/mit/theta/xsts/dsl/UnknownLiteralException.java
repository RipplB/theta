package hu.bme.mit.theta.xsts.dsl;

import hu.bme.mit.theta.core.dsl.ParseException;
import org.antlr.v4.runtime.ParserRuleContext;

public class UnknownLiteralException extends ParseException {
	public UnknownLiteralException(ParserRuleContext ctx, String message) {
		super(ctx, message);
	}
}
