package compiler;

import ast.*;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MatrixBuilderOnTheFlyWithStateVectors {

    private final ProgramNode astRoot;

    private final ObjectOpenCustomHashSet<Object[]> visitedStates;
    final ArrayDeque<Object[]> worklist;
    private final Map<String, String> constants;
    private final List<String> processNames = new ArrayList<>();

    private final Map<String, Integer> processNameToPcIndex;
    private final Map<String, Integer> variableNameToIndex;
    private final Map<Node, Integer> nodeToPcId;
    private final List<Node> pcIdToNode;

    private final int totalVectorSize;
    private final int variableSectionStart;


    //PRODUCER-CONSUMER STRATEGIE
    private record TransitionRecord(int sourceID, int targetID, String rate) {
    }

    //Warteschlange -> BlockingQueue: getrennte Lese- und Schreibrechte; wait() + notify() implementiert
    private final BlockingQueue<TransitionRecord> queue = new LinkedBlockingQueue<>(10000);
    //Mapping
    //Object2IntOpenCustomHashMap aus der fastutil-library. Hochoptimiert. Speichert IDs als primitive int-Werte anstelle von Integer. --> spart Heap
    private final Object2IntOpenCustomHashMap<Object[]> stateToId;
    //Giftpille zum Beenden
    private static final TransitionRecord POISON_PILL = new TransitionRecord(-1, -1, "0");

    java.util.concurrent.atomic.AtomicInteger transitionCount = new java.util.concurrent.atomic.AtomicInteger(0);


    //Setter und Getter für Post-Processing Zugriff
    public Map<Object[], Integer> getStateToIdMap() {
        return this.stateToId;
    }

    public List<String> getProcessNames() {
        return this.processNames;
    }

    public Map<String, Integer> getVariableIndexMap() {
        return this.variableNameToIndex;
    }

    public int getTransitionCount() {
        return transitionCount.get();
    }

    public int getNumStates() {
        return this.visitedStates.size();
    }


    public MatrixBuilderOnTheFlyWithStateVectors(ProgramNode astRoot) {
        ObjectArrayStrategy strategy = new ObjectArrayStrategy();
        this.visitedStates = new ObjectOpenCustomHashSet<>(1 << 20, 0.6f, strategy);
        this.stateToId = new Object2IntOpenCustomHashMap<>(strategy);
        this.stateToId.defaultReturnValue(-1);
        
        this.astRoot = astRoot;
        this.worklist = new ArrayDeque<>();

        this.constants = (astRoot.getPreamble() != null) ? new HashMap<>(astRoot.getPreamble().getConstants()) : new HashMap<>();
        this.processNameToPcIndex = new HashMap<>();
        this.variableNameToIndex = new HashMap<>();
        this.nodeToPcId = new HashMap<>();
        this.pcIdToNode = new ArrayList<>();

        generateProcessNames(astRoot);

        int pcIndexCounter = 0;
        for (String processName : processNames) {
            this.processNameToPcIndex.put(processName, pcIndexCounter++);
        }
        this.variableSectionStart = pcIndexCounter;

        Set<Node> allInstructionNodes = new HashSet<>();
        collectAllInstructionNodes(astRoot, allInstructionNodes);
        int pcIdCounter = 0;
        for (Node instructionNode : allInstructionNodes) {
            this.nodeToPcId.put(instructionNode, pcIdCounter);
            this.pcIdToNode.add(instructionNode);
            pcIdCounter++;
        }

        this.nodeToPcId.put(null, pcIdCounter); // ID for terminal/null states
        this.pcIdToNode.add(null);

        populateVariableIndexMap(astRoot);

        this.totalVectorSize = this.variableSectionStart + this.variableNameToIndex.size();
    }

    private boolean isGlobalTerminal(Object[] state) {
        for (String processName : this.processNames) {
            int pcId = (Integer) state[this.processNameToPcIndex.get(processName)];
            Node instruction = this.pcIdToNode.get(pcId);
            if (instruction != null && !(instruction instanceof EndNode)) {
                return false;
            }
        }
        return true;
    }

    public int buildAndWriteToFile(String outputFile) {
        System.out.println("Starting Producer-Consumer Exploration...");

        String modelType = (astRoot.getPreamble() != null) ? astRoot.getPreamble().getModelType() : "dtmc";

        // Consumer-Thread
        Thread writerThread = new Thread(() -> {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
                while (true) {
                    // .take() blockiert den Thread automatisch, wenn die Queue leer ist.
                    TransitionRecord record = queue.take();

                    // Check für Beenden
                    if (record == POISON_PILL) {
                        break;
                    }

                    writer.write(record.sourceID() + " " + record.targetID() + " " + record.rate() + "\n");
                }

                System.out.println("\n[Consumer] Datei erfolgreich geschlossen.");
            } catch (IOException | InterruptedException e) {
                System.err.println("Fehler im Writer-Thread: " + e.getMessage());
            }
        });

        // Thread starten - er wartet jetzt an der leeren Queue
        writerThread.start();
        //Producer - Thread

        // Initialzustand
        Object[] initialState = createInitialStateVector();
        this.stateToId.put(initialState, 0);
        Object[] initCopy = java.util.Arrays.copyOf(initialState, initialState.length);
        worklist.addLast(initCopy);
        this.visitedStates.add(initCopy);

        //Zustandsraumexploration
        System.out.println(".............Starting state space exploration with interleaving.....");
        long startTime = System.nanoTime();


        long iter = 0L;
        StringBuilder sb = new StringBuilder(1_000_000);

        while (!worklist.isEmpty()) {
            int BIAS = computeBias(worklist.size());
            final Object[] currentState = ((++iter % BIAS) == 0)
                    ? worklist.pollFirst()   // 1/32: "breit"
                    : worklist.pollLast();   // 31/32: "tief"

            if (isGlobalTerminal(currentState)) {
                final int sourceId = stateToId.getInt(currentState);
                try {
                    queue.put(new TransitionRecord(sourceId, sourceId, "1.0"));
                    transitionCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                continue;
            }

            if (currentState == null) continue;

            if (visitedStates.size() % 100000 == 0) {
                printProgress(startTime);
            }

            final java.util.concurrent.atomic.AtomicBoolean hadTransitions =
                    new java.util.concurrent.atomic.AtomicBoolean(false);

            final int sourceId = stateToId.getInt(currentState);

            final TransitionSink sink = (Object[] target, String rate) -> {


                transitionCount.incrementAndGet();

                final Object[] canonical = java.util.Arrays.copyOf(target, target.length);
                int targetId;

                if (visitedStates.add(canonical)) {
                    targetId = stateToId.size();
                    stateToId.put(canonical, targetId);
                    worklist.addLast(canonical);
                } else {
                    targetId = stateToId.getInt(canonical);
                }

                try {
                    queue.put(new TransitionRecord(sourceId, targetId, rate));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            };

            for (String processName : this.processNames) {
                final int pcId = (Integer) currentState[this.processNameToPcIndex.get(processName)];
                final ast.Node instruction = this.pcIdToNode.get(pcId);

                if (instruction == null) continue;

                processInterleavingStep(instruction, currentState, processName, sink);
            }
        }

        try {
            queue.put(POISON_PILL);
            writerThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        System.out.println("\n  State space exploration finished.");
        System.out.println("States found: " + visitedStates.size());
        System.out.println("Transitions found: " + transitionCount.get());


        return this.visitedStates.size();
    }


    private void populateVariableIndexMap(ProgramNode astRoot) {
        if (astRoot == null || astRoot.getRoles() == null) return;

        int varIndexCounter = this.variableSectionStart;

        List<RoleNode> sortedRoles = new ArrayList<>(astRoot.getRoles());
        sortedRoles.sort(Comparator.comparing(RoleNode::getRoleName));

        for (RoleNode role : sortedRoles) {
            Map<String, String> variables = role.getVariablesPerRole();
            if (variables == null) continue;

            List<String> sortedVarNames = new ArrayList<>(variables.keySet());
            Collections.sort(sortedVarNames);

            if (role.getParametricInfo() != null) {
                ParametricInfo params = role.getParametricInfo();
                int start = Integer.parseInt(constants.getOrDefault(params.lowerBound(), params.lowerBound()));
                int end = Integer.parseInt(constants.getOrDefault(params.upperBound(), params.upperBound()));

                for (int i = start; i <= end; i++) {
                    for (String varNameTemplate : sortedVarNames) {
                        // Assuming placeholder is always [i]
                        String concreteVarName = varNameTemplate.replace("[i]", "[" + i + "]");
                        if (this.variableNameToIndex.putIfAbsent(concreteVarName, varIndexCounter) == null) {
                            varIndexCounter++;
                        }
                    }
                }
            } else {
                for (String varName : sortedVarNames) {
                    if (this.variableNameToIndex.putIfAbsent(varName, varIndexCounter) == null) {
                        varIndexCounter++;
                    }
                }
            }
        }
    }

    private void collectAllInstructionNodes(Node node, Set<Node> collectedNodes) {
        if (node == null || collectedNodes.contains(node)) return;

        if (node instanceof BranchNode || node instanceof EndNode || node instanceof RecNode ||
                node instanceof IfThenElseNode || node instanceof InternalActionNode) {
            collectedNodes.add(node);
        }

        if (node.getChildren() != null) {
            for (Node child : node.getChildren()) {
                collectAllInstructionNodes(child, collectedNodes);
            }
        }
    }


    private int computeBias(int size) {
        return 32;
    }


    private String instantiateProcessName(String template, String activeProcessName) {
        if (!template.contains("[i]")) return template;

        int roleIndex = extractRoleIndex(activeProcessName);
        return template.replace("[i]", "[" + roleIndex + "]");
    }

    private Node normalizePcForProcess(Node startNode, CowObjectArray cow, String processName) {
        Node resolved = resolveToNextStableNode(startNode, cow, processName);
        return resolved;
    }

    private void setPc(CowObjectArray cow, String processName, int targetPcId) {
        Integer idx = this.processNameToPcIndex.get(processName);
        if (idx != null) {
            cow.setAt(idx, targetPcId);
        }
    }

    void processInterleavingStep(Node astNode, Object[] sourceState, String activeProcessName, TransitionSink sink) {
        // Terminierung: kein lokaler Self-Loop hier
        if (astNode == null || astNode instanceof EndNode) {
            return;
        }
        CowObjectArray cow = new CowObjectArray(sourceState);

        Node stableStartNode = resolveToNextStableNode(astNode, cow, activeProcessName);
        if (stableStartNode == null) {
            return;
        }

        if (stableStartNode instanceof InternalActionNode internal) {
            String roleNameTemp = internal.getRole();
            String concreteRoleName = roleNameTemp;
            int index = extractRoleIndex(activeProcessName);
            if (roleNameTemp.contains("[")) {
                roleNameTemp = roleNameTemp.split("\\[")[0];

                concreteRoleName = roleNameTemp + "[" + index + "]";
            }

            if (activeProcessName.equals(concreteRoleName)) {
                applyActions(internal, cow, activeProcessName, index);
                Node nextNode = resolveContinuation(internal.getNextStatement());
                Node normalized = normalizePcForProcess(nextNode, cow, activeProcessName);
                applyPcUpdate(cow, activeProcessName, normalized);
                sink.accept(cow.get(), "1.0");
            }
        } else if (stableStartNode instanceof BranchNode branch) {
            String senderTemplate = branch.getSender();

            // 1. SENDER-CHECK
            if (!isRoleMatch(senderTemplate, activeProcessName)) {
                return;
            }

            String receiverTemplate = branch.getReceiver();
            List<Integer> interactionIndices = new ArrayList<>();

            // 2. FALL-UNTERSCHEIDUNG
            // Ist es ein globaler Checkout (Sender ohne Index) an parametrisierte User?
            boolean isOneToMany = !senderTemplate.contains("[i]") && receiverTemplate.contains("[i]");

            if (isOneToMany) {
                // Schleife über alle n User (Checkout-Fall)
                int n = Integer.parseInt(constants.getOrDefault("n", "3"));
                for (int i = 1; i <= n; i++) interactionIndices.add(i);
            } else {
                // Standard-Fall (Dining Cryptographers): Nur ein Index (der des Senders)
                interactionIndices.add(extractRoleIndex(activeProcessName));
            }

            for (int idx : interactionIndices) {
                String concreteReceiver;
                if (isOneToMany) {
                    concreteReceiver = receiverTemplate.replace("[i]", "[" + idx + "]");
                } else {
                    concreteReceiver = instantiateProcessName(receiverTemplate, activeProcessName);
                }

                //Eine Interaktion darf nur stattfinden, wenn der Empfänger bereit ist
                if (!concreteReceiver.equals(activeProcessName)) {
                    int receiverIdx = this.processNameToPcIndex.get(concreteReceiver);
                    int receiverPcId = (Integer) sourceState[receiverIdx];
                    Node receiverNode = this.pcIdToNode.get(receiverPcId);

                    // Wenn der Empfänger NICHT am selben Branch-Knoten steht wie der Sender,
                    // darf diese Interaktion im Interleaving NICHT stattfinden.
                    if (!isReadyToSync(receiverNode, branch, sourceState, concreteReceiver)) {
                        continue;
                    }
                }

                for (ChoicePath choice : branch.getChildren()) {
                    CowObjectArray nextCow = new CowObjectArray(sourceState);

                    // Aktionen ausführen (idx ist i für u[i] oder coin[i])
                    applyActions(choice.getAction(), nextCow, activeProcessName, idx);

                    Node nextNode = resolveContinuation(choice.getNextStatement());
                    int targetPcId = (nextNode == null || nextNode instanceof EndNode)
                            ? this.nodeToPcId.get(null)
                            : this.nodeToPcId.get(nextNode);

                    // PCs updaten
                    setPc(nextCow, activeProcessName, targetPcId);
                    if (!concreteReceiver.equals(activeProcessName)) {
                        setPc(nextCow, concreteReceiver, targetPcId);
                    }

                    sink.accept(nextCow.get(), choice.getRate());
                }
            }
        }
    }

    private void applyPcUpdate(CowObjectArray cow, String activeProcessName, Node nextStable) {
        // 1. Ziel-PC ID ermitteln
        Node targetNode = resolveContinuation(nextStable);
        int targetPcId = (targetNode == null || targetNode instanceof EndNode)
                ? this.nodeToPcId.get(null)
                : this.nodeToPcId.get(targetNode);

        // 2. NUR den PC des aktiven Prozesses aktualisieren
        int pIdx = this.processNameToPcIndex.get(activeProcessName);
        cow.setAt(pIdx, targetPcId);

    }


    private Node resolveToNextStableNode(Node startNode, CowObjectArray cow, String processName) {
        Node current = startNode;
        int roleIndex = extractRoleIndex(processName);
        Set<Node> seen = new HashSet<>();

        while (current instanceof IfThenElseNode || current instanceof RecNode || current instanceof ProtocolNode) {
            if (!seen.add(current)) {
                break;
            }

            if (current instanceof RecNode || current instanceof ProtocolNode) {
                current = resolveContinuation(current);
            } else if (current instanceof IfThenElseNode ifNode) {

                boolean passed = true;
		
		//Evaluation of the guard of the active role
                for (ArrayList<String> conds : ifNode.getConditionsPerRole().values()) {
                    for (String c : conds) {
                        int n = Integer.parseInt(constants.getOrDefault("N", "1"));
                        String instantiated = instantiateCondition(c, roleIndex, n);
                        boolean result = evaluateCondition(instantiated, cow.get());

                        if (!result) {
                            passed = false;
                            break;
                        }
                    }
                    if (!passed) break;
                }

                if (passed) {
                    current = ifNode.getThenStatement();

                } else if (ifNode.getElseStatement() != null) {
                    current = ifNode.getElseStatement();

                } else {
                    //Blocking Guard	
                    return null;
                }
            }

            if (current == null) {
                break;
            }
        }
        return current; //returns the next stable node
    }


    private boolean isReadyToSync(Node current, Node target, Object[] state, String proc) {
        // 1. Strukturelle Knoten (Rec, Protocol) auflösen
        while (current instanceof RecNode || current instanceof ProtocolNode) {
            current = resolveContinuation(current);
        }

        if (current == target) return true;
        if (current == null) return false;

        if (current instanceof IfThenElseNode ifNode) {
            if (ifNode.getConditionsPerRole().containsKey(proc)) {
                // Ich bin der Entscheider -> Ich muss der Wahrheit folgen
                int roleIndex = extractRoleIndex(proc);
                int n = Integer.parseInt(constants.getOrDefault("N", "1"));
                // Hier deine evaluate-Logik nutzen:
                boolean passed = true;
                for (String c : ifNode.getConditionsPerRole().get(proc)) {
                    if (!evaluateCondition(instantiateCondition(c, roleIndex, n), state)) {
                        passed = false;
                        break;
                    }
                }
                return isReadyToSync(passed ? ifNode.getThenStatement() : ifNode.getElseStatement(), target, state, proc);
            } else {
                // Ich bin NICHT der Entscheider -> Ich kann den Zielknoten in JEDEM der Zweige suchen
                return isReadyToSync(ifNode.getThenStatement(), target, state, proc) ||
                        isReadyToSync(ifNode.getElseStatement(), target, state, proc);
            }
        }
        return false;
    }

    public static String instantiateCondition(String cond, int roleIndex, int N) {
        String result = cond;

        // 1. Erst alle indexierten Ausdrücke ersetzen: x[i], x[i+1], p[i], ...
        Pattern pattern = Pattern.compile("(\\w+)\\[(.*?)\\]");
        Matcher matcher = pattern.matcher(result);

        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);   // z.B. coin, agree, p
            String indexExpr = matcher.group(2); // z.B. i, i+1, i-1

            int resolvedIndex = resolveIndex(indexExpr, roleIndex, N);

            String replacement;
            if (varName.equals("p")) {
                // p[i] -> p1, p2, ...
                replacement = "p" + resolvedIndex;
            } else {
                // coin[i] -> coin[1], agree[i+1] -> agree[2], ...
                replacement = varName + "[" + resolvedIndex + "]";
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(sb);
        result = sb.toString();

        // 2. Freies i als ganzes Wort ersetzen
        result = result.replaceAll("\\bi\\b", String.valueOf(roleIndex));

        return result;
    }

    private static int resolveIndex(String expr, int i, int N) {
        expr = expr.replaceAll("\\s+", "");

        if (expr.equals("i")) {
            return i;
        }

        if (expr.equals("i+1")) {
            return (i % N) + 1;
        }

        if (expr.equals("i-1")) {
            return ((i - 2 + N) % N) + 1;
        }

        if (expr.startsWith("i+")) {
            int k = Integer.parseInt(expr.substring(2));
            return ((i - 1 + k) % N) + 1;
        }

        if (expr.startsWith("i-")) {
            int k = Integer.parseInt(expr.substring(2));
            return ((i - 1 - k + N * 100) % N) + 1;
        }

        try {
            return Integer.parseInt(expr);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Unsupported index expression: " + expr);
        }
    }


    private int extractRoleIndex(String activeProcessName) {
        if (!(activeProcessName.contains("["))) {
            return 0;
        }
        int index = activeProcessName.indexOf("[") + 1;
        char roleIndex = activeProcessName.charAt(index);
        return Integer.parseInt(String.valueOf(roleIndex));

    }

    private boolean isRoleMatch(String template, String activeName) {
        if (!template.contains("[")) return template.equals(activeName);
        String tPrefix = template.substring(0, template.indexOf("["));
        String aPrefix = activeName.substring(0, activeName.indexOf("["));
        return tPrefix.equals(aPrefix);
    }


    private String removeQuotes(String s) {
        if (s == null) return null;
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        if (s.length() >= 2 && s.startsWith("'") && s.endsWith("'")) {
            return s.substring(1, s.length() - 1);
        }
        return s.trim();
    }

    private String instantiateActionString(String actionTemplate, String processName, int interactionIndex) {
        if (actionTemplate == null) {
            return actionTemplate;
        }
        if (processName.contains("[") || !actionTemplate.contains("[")) {
            return actionTemplate.replace("[i]", "[" + interactionIndex + "]");

        }


        return actionTemplate;
    }


    private boolean evaluateCondition(String condition, Object[] sourceState) {
        if (condition.contains("&&")) {
            String[] parts = condition.split("&&");
            for (String part : parts) {
                if (!evaluateCondition(part.trim(), sourceState)) return false;
            }
            return true;
        }

        // 2. Operator identifizieren
        // WICHTIG: Die Reihenfolge zählt! ">=" muss vor ">" geprüft werden.
        String op = "";
        if (condition.contains(">=")) op = ">=";
        else if (condition.contains("<=")) op = "<=";
        else if (condition.contains("!=")) op = "!=";
        else if (condition.contains("==")) op = "==";
        else if (condition.contains("=")) op = "=";  // Behandle einfaches = wie ==
        else if (condition.contains(">")) op = ">";
        else if (condition.contains("<")) op = "<";

        if (op.isEmpty()) return true; // Keine Bedingung gefunden -> immer wahr

        // 3. Split an dem Operator
        // Wir nutzen Pattern.quote(op), falls der Operator Sonderzeichen enthält
        String[] parts = condition.split(java.util.regex.Pattern.quote(op));
        if (parts.length < 2) return true;

        String lhsStr = parts[0].trim(); // Linke Seite
        String rhsStr = parts[1].trim(); // Rechte Seite

        // 4. Werte auflösen (Variable, Konstante oder Zahl)
        int lhsValue = resolveValue(lhsStr, sourceState);
        int rhsValue = resolveValue(rhsStr, sourceState);

        // 5. Vergleich durchführen
        return switch (op) {
            case "==", "=" -> lhsValue == rhsValue;
            case "!=" -> lhsValue != rhsValue;
            case ">" -> lhsValue > rhsValue;
            case "<" -> lhsValue < rhsValue;
            case ">=" -> lhsValue >= rhsValue;
            case "<=" -> lhsValue <= rhsValue;
            default -> true;
        };
    }

    /**
     * Hilfsmethode: Löst einen String (Variable, Konstante oder Zahl) in einen Integer auf.
     */
    private int resolveValue(String token, Object[] state) {
        String t = token.replace("(", "").replace(")", "").trim().replaceAll("[\"']", "");

        // 1. Suche im Vektor (Variablen)
        if (state != null && variableNameToIndex.containsKey(t)) {
            return (Integer) state[variableNameToIndex.get(t)];
        }

        if (t.contains("+")) {
            String[] parts = t.split("\\+");
            int result = 0;
            for (String p : parts) {
                result += resolveValue(p.trim(), state); // Rekursiver Aufruf
            }
            return result;
        }

        if (t.contains("-")) {
            // Spezialfall für Ringe: Behandelt "i - 1"
            String[] parts = t.split("-");
            int result = resolveValue(parts[0].trim(), state);
            for (int i = 1; i < parts.length; i++) {
                result -= resolveValue(parts[i].trim(), state);
            }
            return result;
        }

        // 2. Suche in der Preamble (Konstanten)
        // Wir suchen exakt nach dem Namen (z.B. "N" oder "p1")
        if (constants.containsKey(t)) {
            String val = constants.get(t).replaceAll("[;\"\\s]", "");
            try {
                return Integer.parseInt(val);
            } catch (Exception ignored) {
            }
        }

        // 3. Fallback: Zahl
        try {
            return Integer.parseInt(t.replaceAll("[;\\s]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private void applyActions(Node node, CowObjectArray cow, String processName, int interactionIndex) {
        if (node == null) return;
        if (node instanceof ActionNode action) {
            for (String tmpl : action.getAllActionStrings()) {
                String concrete = instantiateActionString(removeQuotes(tmpl), processName, interactionIndex);
                // FIX: Index hier weitergeben
                parseAndApplyAssignments(concrete, cow, processName, interactionIndex);
            }
        }
        if (node instanceof InternalActionNode internal) {
            for (String s : internal.getMessages()) {
                String concrete = instantiateActionString(removeQuotes(s), processName, interactionIndex);
                // FIX: Index hier weitergeben
                parseAndApplyAssignments(concrete, cow, processName, interactionIndex);
            }
        }
    }


    private void parseAndApplyAssignments(String concreteAssignments, CowObjectArray cow, String roleContext, int interactionIndex) {
        if (concreteAssignments == null || concreteAssignments.trim().isEmpty()) return;

        // 1. Klammern entfernen
        String s = concreteAssignments.trim();
        if (s.startsWith("(") && s.endsWith(")")) {
            s = s.substring(1, s.length() - 1).trim();
        }

        // 2. Zerlegen (falls mehrere Zuweisungen durch Komma getrennt sind)
        String[] assignments = s.split(",");
        for (String a : assignments) {
            String entry = a.trim();
            if (entry.isEmpty()) continue;

            String varNameTemplate = "";
            String valueStr = "";

            // Trenne bei ' = oder =
            if (entry.contains("'")) {
                String[] parts = entry.split("'", 2); // Split vor dem Apostroph
                varNameTemplate = parts[0].trim();
                // Rest extrahieren (nach dem = Zeichen)
                String rest = parts[1];
                if (rest.contains("=")) {
                    valueStr = rest.substring(rest.indexOf("=") + 1).trim();
                }
            } else if (entry.contains("=")) {
                String[] parts = entry.split("=", 2);
                varNameTemplate = parts[0].trim();
                valueStr = parts[1].trim();
            }

            // 3. WICHTIG: Den Variablennamen instanziieren (coin[i] -> coin[1])

            String concreteVarName = varNameTemplate.replace("[i]", "[" + interactionIndex + "]");

            // 4. Index im Zustandsvektor suchen
            Integer idx = variableNameToIndex.get(concreteVarName);

            if (idx != null) {
                try {
                    // Nutze resolveValue, falls der Wert eine Konstante oder andere Variable ist
                    int value = resolveValue(valueStr, cow.get());
                    cow.setAt(idx, value);
                } catch (Exception e) {
                    // Fallback für reine Zahlen
                    try {
                        cow.setAt(idx, Integer.parseInt(valueStr));
                    } catch (NumberFormatException ignored) {
                    }
                }
            } else {
                System.err.println("Warning: Variable " + concreteVarName + " not found in index map!");
            }
        }
    }

    private Object[] createInitialStateVector() {
        Object[] initialVector = new Object[this.totalVectorSize];

        Map<String, Node> initialPointers = createInitialInstructionPointers();
        for (Map.Entry<String, Node> entry : initialPointers.entrySet()) {
            String processName = entry.getKey();
            Node startNode = entry.getValue();
            int pcIndex = this.processNameToPcIndex.get(processName);
            int pcId = this.nodeToPcId.get(startNode);
            initialVector[pcIndex] = pcId;
            System.out.println("Initial PC for " + processName + ": " + pcId);
        }

        Map<String, Object> initialVars = determineInitialVariables();
        for (Map.Entry<String, Object> entry : initialVars.entrySet()) {
            String varName = entry.getKey();
            Integer varIndex = this.variableNameToIndex.get(varName);
            if (varIndex != null) {
                initialVector[varIndex] = entry.getValue();
            }
        }
        return initialVector;
    }


    // --- Helper methods for condition evaluation, AST traversal etc. ---
    // (These are mostly unchanged but are included for completeness)

    private Map<String, Object> determineInitialVariables() {
        Map<String, Object> globalInitialVars = new HashMap<>();
        if (astRoot == null || astRoot.getRoles() == null) return globalInitialVars;

        for (RoleNode role : astRoot.getRoles()) {
            Map<String, String> variableTemplates = role.getVariablesPerRole();
            if (variableTemplates == null) continue;

            if (role.getParametricInfo() != null) {
                ParametricInfo params = role.getParametricInfo();
                int start = Integer.parseInt(constants.getOrDefault(params.lowerBound(), params.lowerBound()));
                int end = Integer.parseInt(constants.getOrDefault(params.upperBound(), params.upperBound()));
                for (int i = start; i <= end; i++) {
                    for (Map.Entry<String, String> templateEntry : variableTemplates.entrySet()) {
                        String concreteVarName = templateEntry.getKey().replace("[i]", "[" + i + "]");
                        globalInitialVars.put(concreteVarName, parseInitialValue(templateEntry.getValue()));
                    }
                }
            } else {
                for (Map.Entry<String, String> entry : variableTemplates.entrySet()) {
                    globalInitialVars.put(entry.getKey(), parseInitialValue(entry.getValue()));
                }
            }
        }
        return globalInitialVars;
    }

    private Integer parseInitialValue(String valueTemplate) {
        if (valueTemplate == null || valueTemplate.trim().isEmpty()) return 0;
        try {
            return Integer.parseInt(valueTemplate.trim());
        } catch (NumberFormatException e) {
            return 0; // Default for non-integer initializers like "[]"
        }
    }

    private Node findProtocolStartNode(ProgramNode root, String protocolName) {
        if (root == null || root.getProtocols() == null || protocolName == null) return null;
        for (ProtocolNode protocol : root.getProtocols()) {
            if (protocolName.trim().equals(protocol.getName().trim())) {
                return (protocol.getChildren() != null && !protocol.getChildren().isEmpty()) ? protocol.getChildren().get(0) : null;
            }
        }
        System.err.println("Error: Target protocol named '" + protocolName + "' was NOT FOUND in the AST.");
        return null;
    }

    private Node resolveContinuation(Node continuation) {
        if (continuation instanceof RecNode recNode) {
            return findProtocolStartNode(this.astRoot, recNode.getName());
        }
        return continuation;
    }


    private void generateProcessNames(ProgramNode root) {
        if (root == null || root.getRoles() == null) return;
        for (RoleNode role : root.getRoles()) {
            if (role.getParametricInfo() != null) {
                ParametricInfo params = role.getParametricInfo();
                int start = Integer.parseInt(constants.getOrDefault(params.lowerBound(), params.lowerBound()));
                int end = Integer.parseInt(constants.getOrDefault(params.upperBound(), params.upperBound()));
                String baseName = role.getRoleName().replace("[i]", "");
                for (int i = start; i <= end; i++) {
                    this.processNames.add(baseName + "[" + i + "]");
                }
            } else {
                this.processNames.add(role.getRoleName());
            }
        }
    }

    private Map<String, Node> createInitialInstructionPointers() {
        Map<String, Node> initialPointers = new HashMap<>();
        String startProtocolName = null;
        if (astRoot.getProtocols() != null && !astRoot.getProtocols().isEmpty()) {
            startProtocolName = astRoot.getProtocols().get(0).getName();
        }

        if (startProtocolName == null) {
            return new HashMap<>();
        }

        Node startNode = findProtocolStartNode(this.astRoot, startProtocolName);
        if (startNode != null) {
            for (String processName : this.processNames) {
                initialPointers.put(processName, startNode);
            }
        }
        return initialPointers;
    }

    private void printProgress(long startTime) {
        long elapsedNanos = System.nanoTime() - startTime;
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        double statesPerSecond = (elapsedSeconds > 0) ? this.visitedStates.size() / elapsedSeconds : 0;
        System.out.printf(
                "\rStates Found: %,d | Worklist Size: %,d | Time: %.2f s | Speed: %,.0f states/sec",
                this.visitedStates.size(),
                this.worklist.size(),
                elapsedSeconds,
                statesPerSecond
        );
    }

}
