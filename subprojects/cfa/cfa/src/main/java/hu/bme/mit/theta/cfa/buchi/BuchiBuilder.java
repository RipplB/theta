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

import hu.bme.mit.theta.cfa.CFA;
import hu.bme.mit.theta.cfa.dsl.gen.LTLGrammarLexer;
import hu.bme.mit.theta.cfa.dsl.gen.LTLGrammarParser;
import hu.bme.mit.theta.common.logging.Logger;
import hu.bme.mit.theta.core.decl.Decl;
import hu.bme.mit.theta.core.decl.VarDecl;
import hu.bme.mit.theta.core.stmt.Stmts;
import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.core.type.booltype.BoolExprs;
import hu.bme.mit.theta.core.type.booltype.BoolType;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerException;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.hoa.HoaWriter;
import owl.collections.Either;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.parser.LtlParser;
import owl.translations.ltl2nba.ProductState;
import owl.translations.ltl2nba.SymmetricNBAConstruction;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class BuchiBuilder implements HOAConsumer {

    private final CFA.Builder builder;
    private final Logger logger;
    private Integer initLocNumber = null;
    private List<String> aps;
    private final Map<Integer, CFA.Loc> locations;
    private Map<String, Expr<BoolType>> swappedExpressions;

    private BuchiBuilder(final Logger logger) {
        builder = CFA.builder();
        this.logger = logger;
        locations = new HashMap<>();
    }

    public static CFA of(String ltlExpression, Collection<VarDecl<?>> variables, final Logger logger) throws HOAConsumerException {
        LTLGrammarParser.ModelContext modelContext = new LTLGrammarParser(
                new CommonTokenStream(
                        new LTLGrammarLexer(CharStreams.fromString(ltlExpression))
                )
        ).model();
        Map<String, VarDecl<?>> namedVariables = variables.stream().collect(Collectors.toMap(Decl::getName, v -> v));
        Map<String, Integer> literalToIntMap = new HashMap<>();
        ToStringVisitor toStringVisitor = new ToStringVisitor(new APGeneratorVisitor(namedVariables, literalToIntMap));
        String swappedLtl = toStringVisitor.visitModel(modelContext);
        LabelledFormula negatedLtl = LtlParser.parse(swappedLtl).not();
        Automaton<Either<Formula, ProductState>, BuchiAcceptance> oautomaton = SymmetricNBAConstruction.of(BuchiAcceptance.class).apply(negatedLtl);
        BuchiBuilder buchiBuilder = new BuchiBuilder(logger);
        buchiBuilder.swappedExpressions = toStringVisitor.aps;
        HoaWriter.write(oautomaton, buchiBuilder, false);
        return buchiBuilder.builder.build();
    }

    private CFA.Loc getOrCreateLocation(int locName) {
        return locations.computeIfAbsent(locName, i -> builder.createLoc(String.valueOf(i)));
    }
    
    private Expr<BoolType> apBoolExpressionToInternal(BooleanExpression<AtomLabel> booleanExpression) {
        return switch (booleanExpression.getType()) {
            case EXP_AND -> BoolExprs.And(apBoolExpressionToInternal(booleanExpression.getLeft()), apBoolExpressionToInternal(booleanExpression.getRight()));
            case EXP_OR -> BoolExprs.Or(apBoolExpressionToInternal(booleanExpression.getLeft()), apBoolExpressionToInternal(booleanExpression.getRight()));
            case EXP_NOT -> BoolExprs.Not(apBoolExpressionToInternal(booleanExpression.getLeft()));
            case EXP_TRUE -> BoolExprs.True();
            case EXP_ATOM -> swappedExpressions.get(aps.get(Integer.parseInt(booleanExpression.getAtom().toString())));
            default -> BoolExprs.False();
        };
    }

    @Override
    public boolean parserResolvesAliases() {
        return false;
    }

    @Override
    public void notifyHeaderStart(String s) {
        logger.write(Logger.Level.VERBOSE, "HOA consumer header: %s%n", s);
    }

    @Override
    public void setNumberOfStates(int i) {
        logger.write(Logger.Level.VERBOSE, "HOA automaton has %d states%n", i);
    }

    @Override
    public void addStartStates(List<Integer> list) throws HOAConsumerException {
        if (list.isEmpty() || list.get(0) == null)
            return;
        if (list.size() != 1 || initLocNumber != null)
            throw new HOAConsumerException("HOA automaton should have exactly 1 starting location%n");
        initLocNumber = list.get(0);
    }

    @Override
    public void addAlias(String s, BooleanExpression<AtomLabel> booleanExpression) {
        // currently does not get called by the Owl library
    }

    @Override
    public void setAPs(List<String> list) {
        if (aps == null)
            aps = List.copyOf(list);
        else
            aps.addAll(list);
    }

    @Override
    public void setAcceptanceCondition(int i, BooleanExpression<AtomAcceptance> booleanExpression) throws HOAConsumerException {
        logger.write(Logger.Level.VERBOSE, "Acceptance condition: %s%n", booleanExpression);
    }

    @Override
    public void provideAcceptanceName(String s, List<Object> list) {
        logger.write(Logger.Level.VERBOSE, "Acceptance name received: %s%n", s);
        list.forEach(o -> logger.write(Logger.Level.VERBOSE, "\tobject under acceptance: %s%n", o));
    }

    @Override
    public void setName(String s) throws HOAConsumerException {
        logger.write(Logger.Level.VERBOSE, "Automaton named {}%n", s);
    }

    @Override
    public void setTool(String s, String s1) {
        logger.write(Logger.Level.VERBOSE, "Tool named %s %s%n", s, s1);
    }

    @Override
    public void addProperties(List<String> list) {
        if (list.isEmpty())
            return;
        logger.write(Logger.Level.VERBOSE, "Properties:%n");
        list.forEach(prop -> logger.write(Logger.Level.VERBOSE, "%s", prop));
        logger.write(Logger.Level.VERBOSE, "%n");
    }

    @Override
    public void addMiscHeader(String s, List<Object> list) {
        // we don't really care of these yet
    }

    @Override
    public void notifyBodyStart() {
        // no action needed
    }

    @Override
    public void addState(int i, String s, BooleanExpression<AtomLabel> booleanExpression, List<Integer> list) {
        getOrCreateLocation(i);
    }

    @Override
    public void addEdgeImplicit(int i, List<Integer> list, List<Integer> list1) {
        // currently does not get called by the Owl library
    }

    @Override
    public void addEdgeWithLabel(int i, BooleanExpression<AtomLabel> booleanExpression, List<Integer> list, List<Integer> list1) throws HOAConsumerException {
        CFA.Loc from = getOrCreateLocation(i);
        CFA.Loc to = getOrCreateLocation(list.get(0));
        CFA.Edge edge = builder.createEdge(from, to, Stmts.Assume(apBoolExpressionToInternal(booleanExpression)));
        if (list1 != null && !list1.isEmpty())
            builder.setAcceptingEdge(edge);
    }

    @Override
    public void notifyEndOfState(int i) {
        // no action needed
    }

    @Override
    public void notifyEnd() throws HOAConsumerException {
        if (initLocNumber == null)
            throw new HOAConsumerException("No initial location named");
        builder.setInitLoc(locations.get(initLocNumber));
    }

    @Override
    public void notifyAbort() {
        // never gets called yet
    }

    @Override
    public void notifyWarning(String s) throws HOAConsumerException {
        throw new HOAConsumerException(s);
    }
}
