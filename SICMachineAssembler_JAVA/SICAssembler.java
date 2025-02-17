package SICMachineAssembler_JAVA;

import java.io.*;
import java.util.*;

public class SICAssembler {

    // SIC Operation Table
    static Map<String, String> OPTAB = new LinkedHashMap<>() {
        {
            put("LDA", "00");
            put("STA", "0C");
            put("LDCH", "50");
            put("STCH", "54");
            put("ADD", "18");
            put("SUB", "1C");
            put("MUL", "20");
            put("DIV", "24");
            put("AND", "40");
            put("OR", "44");
            put("COMP", "28");
            put("TIX", "2C");
            put("J", "3C");
            put("JEQ", "30");
            put("JGT", "34");
            put("JLT", "38");
            put("JSUB", "48");
            put("RSUB", "4C");
            put("TD", "E0");
            put("RD", "D8");
            put("WD", "DC");
            put("WORD", null); // Non-machine instructions
            put("RESW", null);
            put("RESB", null);
            put("BYTE", null);
        }

    };


    static Map<String, Integer> SYMTAB = new LinkedHashMap<>(); // Symbol Table
    static int LOCCTR; // Location Counter
    static int START_ADDRESS;

    public static void main(String[] args) throws IOException {
        String inputFile = "C:\\Users\\omars\\OneDrive\\Desktop\\ProjectSystemLastTry\\START 2000.txt";
        String intermediateFile = "C:\\Users\\omars\\OneDrive\\Desktop\\ProjectSystemLastTry\\intermediate.txt";
        String symbolTableFile = "C:\\Users\\omars\\OneDrive\\Desktop\\ProjectSystemLastTry\\symbol_table.txt";
        String outputFile = "C:\\Users\\omars\\OneDrive\\Desktop\\ProjectSystemLastTry\\output.txt";
        String optabFile = "C:\\Users\\omars\\OneDrive\\Desktop\\ProjectSystemLastTry\\used_op_table.txt";
        String opTableFile = "C:\\Users\\omars\\OneDrive\\Desktop\\ProjectSystemLastTry\\op_table.txt";

        // Load Opcode Table
        OPTAB = loadOpTable(opTableFile);

        // Perform Pass 1
        pass1(inputFile, intermediateFile, symbolTableFile, optabFile);

        // Perform Pass 2
        pass2(intermediateFile, outputFile, symbolTableFile, optabFile);

        System.out.println("SIC Assembler Execution Completed Successfully!");
    }

    // Method to Load OPTAB from a File
    public static HashMap<String, String> loadOpTable(String opTableFile) throws IOException {
        HashMap<String, String> opTable = new HashMap<>();
        BufferedReader reader = new BufferedReader(new FileReader(opTableFile));
        String line;

        while ((line = reader.readLine()) != null) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 2) {
                opTable.put(parts[0], parts[1]);
            }
        }

        reader.close();
        return opTable;
    }

    // Pass 1: Generate Symbol Table and Intermediate File
    public static void pass1(String inputFile, String intermediateFile, String symbolTableFile, String opTableFile) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(inputFile));
        BufferedWriter interWriter = new BufferedWriter(new FileWriter(intermediateFile));
        LOCCTR = 0;

        String line = reader.readLine();
        if (line != null) {
            String[] tokens = line.trim().split("\\s+");
            if (tokens[0].equalsIgnoreCase("START")) {
                START_ADDRESS = Integer.parseInt(tokens[1], 16);
                LOCCTR = START_ADDRESS;
                interWriter.write(String.format("%04X\t%s%n", LOCCTR, line));
                line = reader.readLine();
            }
        }

        while (line != null) {
            String[] tokens = line.trim().split("\\s+");
            if (tokens.length == 0) {
                line = reader.readLine();
                continue;
            }

            String label = tokens.length > 2 ? tokens[0] : null;
            String opcode = tokens.length > 2 ? tokens[1] : tokens[0];
            String operand = tokens.length > 2 ? tokens[2] : tokens.length > 1 ? tokens[1] : null;

            // Add label to Symbol Table
            if (label != null) {
                if (SYMTAB.containsKey(label)) {
                    System.err.println("Error: Duplicate symbol " + label);
                } else {
                    SYMTAB.put(label, LOCCTR);
                }
            }

            // Write intermediate line
            interWriter.write(String.format("%04X\t%s%n", LOCCTR, line));

            // Increment LOCCTR based on opcode
            if (OPTAB.containsKey(opcode)) {
                LOCCTR += 3; // Machine instructions are 3 bytes
            } else if (opcode.equalsIgnoreCase("WORD")) {
                LOCCTR += 3;
            } else if (opcode.equalsIgnoreCase("RESW")) {
                LOCCTR += 3 * Integer.parseInt(operand);
            } else if (opcode.equalsIgnoreCase("RESB")) {
                LOCCTR += Integer.parseInt(operand);
            } else if (opcode.equalsIgnoreCase("BYTE")) {
                LOCCTR += operand.startsWith("C'") ? operand.length() - 3 : 1;
            }

            line = reader.readLine();
        }

        // Write Symbol Table to File
        BufferedWriter symWriter = new BufferedWriter(new FileWriter(symbolTableFile));
        for (Map.Entry<String, Integer> entry : SYMTAB.entrySet()) {
            symWriter.write(String.format("%s\t%04X%n", entry.getKey(), entry.getValue()));
        }
        symWriter.close();

        // Write Operation Table to File
        BufferedWriter opWriter = new BufferedWriter(new FileWriter(opTableFile));
        for (Map.Entry<String, String> entry : OPTAB.entrySet()) {
            if (entry.getValue() != null) {
                opWriter.write(String.format("%s\t%s%n", entry.getKey(), entry.getValue()));
            }
        }
        opWriter.close();

        reader.close();
        interWriter.close();

        System.out.println("Pass 1 Completed: Intermediate file, Symbol Table, and Op Table generated.");
    }

    // Pass 2: Generate Object Program, Main Output, and Separate OPTAB File
    public static void pass2(String intermediateFile, String outputFile, String symbolTableFile, String optabFile) throws IOException {
        BufferedReader interReader = new BufferedReader(new FileReader(intermediateFile));
        BufferedWriter outputWriter = new BufferedWriter(new FileWriter(outputFile));
        BufferedWriter optabWriter = new BufferedWriter(new FileWriter(optabFile));

        HashSet<String> usedOpcodes = new HashSet<>(); // To track opcodes used in the input
        String line;

        // Variables for Object Program
        StringBuilder textRecord = new StringBuilder();
        String headerRecord = "";
        String endRecord = "";
        int startAddress = 0;
        int programLength = 0;
        String firstExecutable = "";

        boolean isHeaderWritten = false;

        // Write Header for Main Output File
        outputWriter.write(String.format("%-8s%-10s%-10s%-10s%-10s%n", "Address", "Label", "Opcode", "Operand", "Object Code"));

        while ((line = interReader.readLine()) != null) {
            String[] parts = line.trim().split("\\s+");
            int address = Integer.parseInt(parts[0], 16);
            String[] tokens = Arrays.copyOfRange(parts, 1, parts.length);

            String label = tokens.length > 2 ? tokens[0] : "";
            String opcode = tokens.length > 2 ? tokens[1] : tokens[0];
            String operand = tokens.length > 2 ? tokens[2] : tokens.length > 1 ? tokens[1] : "";
            String objectCode = "";

            if (!isHeaderWritten && "START".equals(opcode)) {
                startAddress = address;
                headerRecord = String.format("H^%-6s^%06X^%06X", label, startAddress, programLength);
                isHeaderWritten = true;
                firstExecutable = String.format("%04X", address); // Set first executable address
            }

            if (OPTAB.containsKey(opcode)) {
                usedOpcodes.add(opcode); // Track the opcode
                objectCode = OPTAB.get(opcode);
                if (!operand.isEmpty() && SYMTAB.containsKey(operand)) {
                    objectCode += String.format("%04X", SYMTAB.get(operand));
                } else {
                    objectCode += "0000";
                }
            } else if ("BYTE".equals(opcode)) {
                if (operand.startsWith("C")) { // Convert characters to ASCII
                    objectCode = operand.substring(2, operand.length() - 1).chars()
                            .mapToObj(c -> String.format("%02X", c))
                            .reduce("", String::concat);
                } else if (operand.startsWith("X")) { // Hexadecimal value
                    objectCode = operand.substring(2, operand.length() - 1);
                }
            } else if ("WORD".equals(opcode)) {
                objectCode = String.format("%06X", Integer.parseInt(operand));
            }

            // Generate Text Record
            if (!objectCode.isEmpty()) {
                if (textRecord.length() == 0) {
                    textRecord.append(String.format("T^%06X^", address)); // Start a new text record
                }
                textRecord.append(objectCode).append("^");
            }

            // Write the formatted line to the output file
            outputWriter.write(String.format("%04X    %-10s%-10s%-10s%-10s%n", address, label, opcode, operand, objectCode));
        }

        // Write End Record
        endRecord = String.format("E^%06X", Integer.parseInt(firstExecutable, 16));

        // Write Object Program to the Output File
        outputWriter.write("\n--- Object Program ---\n");
        outputWriter.write(headerRecord + "\n");
        outputWriter.write(textRecord.substring(0, textRecord.length() - 1) + "\n"); // Remove last "^"
        outputWriter.write(endRecord + "\n");

        // Write Relevant Opcode Table to a Separate File
        for (String opcode : usedOpcodes) {
            optabWriter.write(String.format("%-10s%-10s%n", opcode, OPTAB.get(opcode)));
        }

        interReader.close();
        outputWriter.close();
        optabWriter.close();

        System.out.println("Pass 2 Completed: Object program, main output, and OPTAB file generated.");
    }



}
