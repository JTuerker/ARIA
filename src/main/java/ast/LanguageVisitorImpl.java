package ast;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.Token;
import parser.LanguageBaseVisitor;
import parser.LanguageParser.*;

public class LanguageVisitorImpl extends LanguageBaseVisitor<Node> {


	@Override
	public Node visitProtocol(ProtocolContext ctx) {
		//1. get ModelType from preamble
		PreambleNode preamble = null;
		if (ctx.preamble() != null) {
			preamble = visitPreamble(ctx.preamble());
		}

		//2. global initial state (e.g. x = 0)
		if (ctx.varDef() != null) {
			String varName = ctx.varDef().CHAR().getText();
			String value = ctx.varDef().INTEGER().getText();
			int constValue = Integer.parseInt(value);
		}
		// 3. get variables per role
		ArrayList<RoleNode> roles = new ArrayList<>();
		if (ctx.roleDef() != null) {

            for (int i = 0; i < ctx.roleDef().size(); i++) {
                String roleName = "";

                ParametricInfo parametricInfo = null;
                HashMap<String, String> variablesPerRole = new HashMap<>();

                String roleDefContext = ctx.roleDef(i).getText();

                String lhsPart = "";
                int colonIndex = roleDefContext.indexOf(':');
                if (colonIndex != -1) {
                    //Rolle enthält Variablen
                    lhsPart = roleDefContext.substring(0, colonIndex).trim();
                    String rhsPart = roleDefContext.split(":")[1].trim();
                    String[] variablesInRole = rhsPart.split(",");
                    for(String s : variablesInRole){
                        s = s.replace(";", "");
                        String str = removeQuotes(s);
                        String key = str.split("=")[0].trim();
                        String val = str.split("=")[1].trim();
                        variablesPerRole.put(key, val);
                    }

                } else {
                    //Rolle enthält keine Variablen
                    lhsPart = roleDefContext.trim();
                    if (lhsPart.endsWith(";")) {
                        lhsPart = lhsPart.substring(0, lhsPart.length() - 1);
                    }
                }

                roleName = lhsPart.split("->")[0].trim();

                Pattern p = Pattern.compile("i\\s*in\\s*\\[\\s*(?<low>[^\\[\\]]+?)\\s*\\.{2,3}\\s*(?<high>[^\\[\\]]+?)\\s*\\]");
                Matcher m = p.matcher(lhsPart);
                if(m.find()) {
                    //Parametric role
                    String low = m.group("low").trim();
                    String high = m.group("high").trim();
                    parametricInfo = new ParametricInfo(low, high, preamble.getConstants());
                }
                else {
                    parametricInfo = null;
                }
                RoleNode role = new RoleNode(roleName,parametricInfo,variablesPerRole);
                roles.add(role);





            }
        }


		ArrayList<ProtocolNode> protocolDefinitions = new ArrayList<>();
		for (int i = 0; i < ctx.protocolID().size(); i++) {
			String name = ctx.protocolID().get(i).getText();
			ArrayList<Node> blockStat = new ArrayList<Node>();
			for (StatementContext el : ctx.blockStatement().get(i).statement()) {
				blockStat.add(visitStatement(el));
			}

			protocolDefinitions.add(new ProtocolNode(name, blockStat));
		}
		return new ProgramNode(preamble, roles, protocolDefinitions);
	}



	/**
	 * Visits the preamble section of the protocol and extracts relevant metadata.
	 * <p>
	 * This method checks for the model type declaration (e.g., "ctmc" or "dtmc")
	 * and sets a boolean flag accordingly. "dtmc" is assumed by default.
	 * It currently ignores variable declarations,
	 * </p>
	 *
	 * @param ctx The context object representing the parsed preamble rule.
	 * @return A {@link PreambleNode} containing the model type flag.
	 */
	@Override
	public PreambleNode visitPreamble(PreambleContext ctx) {
		boolean isCtmc = false;
		if(ctx.modelType() != null) {
			if (ctx.modelType().DOUBLE_STRING().getText().equals("\"ctmc\"")) {
				isCtmc = true;
			}
		}
		Map<String, String> constants = new HashMap<>();


            for (VariableDeclContext vars : ctx.variableDecl()) {
                String var = removeQuotes(vars.DOUBLE_STRING().getText());
                if(!var.equals("dtmc") && !var.equals("ctmc")) {
                    String[] parts = var.split("=");
                    Pattern p = Pattern.compile("\\bconst\\s+\\w+\\s+([A-Za-z_]\\w*)\\b(?=\\s*(?:=|;|$))");
                    Matcher m = p.matcher(parts[0]);
                    String key = "";
                    if (m.find()) {
                        key = m.group(1);
                    }
                    String val = parts[1].replace(";", "").trim();
                    constants.put(key, val);
                }
            }
		return new PreambleNode(isCtmc, constants);
	}

	@Override
	public Node visitStatement(StatementContext ctx) {
		if (ctx.branch() != null) {
			return visitBranch(ctx.branch());
		} else if (ctx.ifThenElse() != null) {
			return visitIfThenElse(ctx.ifThenElse());
		} else if (ctx.rec() != null) {
			return visitRec(ctx.rec());
		} else if (ctx.end() != null) {
			return visitEnd(ctx.end());
		}
		else if(ctx.internalAction() != null) {
			return visitInternalAction(ctx.internalAction());
		}
		System.err.println("Warning: visitStatement encountered an unknown statement structure " + ctx.getText());
		return null;
	}


	@Override
	public Node visitEnd(EndContext ctx) {
		return new EndNode();
	}

	@Override
	public MessageNode visitMessage(MessageContext ctx) {
		ArrayList<Node> parts = new ArrayList<>();
		ActionNode actions = null;
		if (ctx.actions() != null) {

			ArrayList<String> actionsA = new ArrayList<>();
			ArrayList<String> actionsB = new ArrayList<>();

			if (ctx.actions().action != null && !ctx.actions().action.isEmpty()) {
				List<? extends Token> actionTokens = ctx.actions().action;
				int numActionTokens = actionTokens.size();

				if (numActionTokens >= 1) {
					// HIER REINIGEN: removeQuotes aufrufen und dann sanitize
					String rawA = removeQuotes(actionTokens.get(0).getText());
					String actionA = sanitize(rawA);
					actionsA.add(actionA);
				}

				if (numActionTokens >= 2) {
					String rawB = removeQuotes(actionTokens.get(1).getText());
					String actionB = sanitize(rawB);
					if (!actionB.isEmpty()) {
						String[] actionParts = actionB.split(" ");
						Collections.addAll(actionsB, actionParts);
					}
				}
			}

			actions = new ActionNode(actionsA, actionsB);
			return new MessageNode(actions, parts);
		}
		else if (ctx.loop() != null) { // Loop Case
			for (LoopContext lCtx : ctx.loop()) {
				String op = lCtx.op.getText();
				char upperBound = lCtx.upperBound.getText().charAt(0);

				// Das Template (z.B. "v[i]'=v[i-1]")
				String template = sanitize(removeQuotes(lCtx.DOUBLE_STRING().getText()));

				// Die Ziel-Rolle (z.B. "p[i]")
				String targetRole = lCtx.role().getText();

				// Erzeuge eine echte LoopNode für den AST
				parts.add(new LoopNode(' ', op, upperBound, template, targetRole));
			}
		}
		return new MessageNode(actions, parts);
	}


	private String sanitize(String s) {
		if (s == null) return "";
		// Dieser Regex behält nur Standard-Buchstaben, Zahlen und Logik-Symbole.
		// Alles wie Byte 193 oder 179 wird gelöscht.
		return s.replaceAll("[^\\x20-\\x7E]", "").trim();
	}

	/**
	 * Helper to remove quotes from labels and actions
	 * @param s
	 * @return
	 */
	private String removeQuotes(String s) {
		if (s == null) return null;
		if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
			return s.substring(1, s.length() - 1);
		}
		if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) {
			return s.substring(1, s.length() - 1);
		}
		return s;
	}

	@Override
	public Node visitBranch(BranchContext ctx) {
		MessageNode message;
		ArrayList<ChoicePath> choices = new ArrayList<>();

        //Get Sender and Receivers
        String sender = ctx.inputRole.getText();
        String receivers = "";
        List<RoleContext> outputRoles = ctx.outputRole;
        for(RoleContext roleCtx : outputRoles){
            receivers = roleCtx.getText();
        }

		if (!ctx.branchStat().isEmpty()) {
            //Real Branch with choices
			for (BranchStatContext branchStatContext : ctx.branchStat()) {
				String rate = removeQuotes(branchStatContext.rate().getText());
				UpdatesContext updatesContext = branchStatContext.updates();
				MessageContext messageContext = updatesContext.upds;
				if(updatesContext != null && messageContext != null){
					message = visitMessage(messageContext);
					choices.add(new ChoicePath(rate, message.getActions(), visitStatement(branchStatContext.statement())));
				}
			}

        } else {
            //CommStat
			String rate = removeQuotes(ctx.commStat().rate().DOUBLE_STRING().getText());
			if (ctx.commStat().updates() != null && ctx.commStat().updates().upds != null) {
				UpdatesContext updatesContext = ctx.commStat().updates();
				MessageContext messageContext = updatesContext.upds;
				message = visitMessage(messageContext);
				choices.add(new ChoicePath(rate, message.getActions(),visitStatement(ctx.commStat().statement())));
				}

        }
        return new BranchNode(sender, receivers, choices);
    }

		@Override
		public Node visitRec(RecContext ctx) {
			return new RecNode(ctx.protocolID().ID().getText());
		}



/*	@Override
//TODO implement Loop
	public Node visitLoop(LoopContext ctx) {
		String role = "";
		if (ctx.role().roleIndex() != null) {
			role = ctx.role().roleIndex().ID().getText() + "[" + ctx.role().roleIndex().index().CHAR().getText() + "]";
		} else {
			role = ctx.role().roleGroup().ID().getText();
		}
		String message = ctx.DOUBLE_STRING().getText().substring(1, ctx.DOUBLE_STRING().getText().length() - 1);

		return new LoopNode();
	}


*/


	public Node visitIfThenElse(IfThenElseContext ctx) {
		ArrayList<CondContext> conditionContexts = new ArrayList<>(ctx.cond());
        List<RoleContext> roleContexts = ctx.role();
        HashMap<String, ArrayList<String>> conditionsPerRole = new HashMap<>();
        for(int i = 0; i < roleContexts.size(); i++){
            String role = removeQuotes(roleContexts.get(i).getText().trim());
            conditionsPerRole.put(role, new ArrayList<String>());
            String condition = removeQuotes(conditionContexts.get(i).DOUBLE_STRING().getText());
            if(condition.contains("&&")){
                String[] conditionParts = condition.split(("&&"));
                for(String s : conditionParts){
                    conditionsPerRole.get(role).add(s.trim());
                }
            }else{
                conditionsPerRole.get(role).add(condition.trim());
            }
		}


		Node thenStatement = null;
		if(ctx.thenStat != null){
			thenStatement = visitStatement(ctx.thenStat);
		}
		Node elseStatement = null;
		if (ctx.elseStat != null) {
			elseStatement = visitStatement(ctx.elseStat);
		}
		return new IfThenElseNode(conditionsPerRole, thenStatement, elseStatement);
	}





	public Node visitInternalAction(InternalActionContext ctx) {
		// 1. Rate extrahieren (Anführungszeichen entfernen)
		String rawRate = ctx.rate().DOUBLE_STRING().getText();
		String rate = rawRate.substring(1, rawRate.length() - 1);

		// 2. Die Nachricht besuchen, um die Variablen-Updates zu erhalten
		MessageNode upds = visitMessage(ctx.message());

		// 3. Rolle extrahieren (z.B. @crypt[i])
		String role = ctx.role().getText();

		// 4. Die Liste der Action-Strings aus dem MessageNode holen
		// Wir nehmen alle ActionStrings aus dem ActionNode-Objekt
		ArrayList<String> actionStrings = new ArrayList<>();
		if (upds != null && upds.getActions() != null) {
			actionStrings.addAll(upds.getActions().getAllActionStrings());
		}

		// 5. Das nächste Statement besuchen
		Node nextStatement = visitStatement(ctx.statement());

		// 6. Das InternalActionNode mit der BEFÜLLTEN Liste zurückgeben
		// HIER: actionStrings statt new ArrayList<>() übergeben!
		return new InternalActionNode(rate, role, actionStrings, nextStatement);
	}


	}

