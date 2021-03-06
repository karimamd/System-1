package Table;
//just a first comment to try commits

import Table.Objectcode.*;

import java.io.*;
import java.util.*;

/**
 * Created by ICY on 3/14/2017.
 */
public class Assembler {

    private final Map<String, OPERATION> opTable;

    private final Map<String, Integer> registerTable;
    //Q:is making symbol table final a correct move ? this means it can only be intialized in the constructor once
    //if we are going to do that then we need to put the constructor in pass 1
    private final Map<String, Integer> symbolTable;
    private int location;
    private int startAddress;
    private int firstExecAddress;
    private int Length;
    private int baseAddress;

    public Assembler() {
        //Pointing the optable to the operation in the instruction class
        opTable = Instruc.getOPERATIONTable();
        //Pointing the registertable to the register inside the instruction class
        registerTable = Instruc.getRegisterTable();
        symbolTable = new HashMap<>();
        //Initializing the symbol table
        symbolTable.put(null, 0);


    }

    public static void main(String args[]) {
        try {
            Assembler assembler = new Assembler();
            //  File assembly = new File ("copy.asm");
            assembler.run(new File("input_fibonacci.txt"), new File("Listing.txt"), new File("symb.txt"), new File("HTMI.o"));

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

    }

    /**
     * THIS METHOD READ THE INPUT FILE AND SEND IT TO BE PASSED TO PASS 1 AND PASS2 THEN RETURN THE OUTPUT INTO FILES
     *
     * @param input   ASSEMBLY FILE
     * @param output  LISTING FILE
     * @param output2 SYMBOL TABLE FILE
     * @param output3 HTMI FILE
     * @throws IOException            CHECK IF FILE ERROR
     * @throws ClassNotFoundException IF CLASS IS NOT AVAILABLE
     */
    void run(File input, File output, File output2, File output3) throws IOException, ClassNotFoundException {
        //creating temporary file to take the output from pass 1 to be translated to object code in pass 2
        File intermediateFile = new File(".assembler.tmp");
        try {
            intermediateFile.createNewFile();
            pass1(input, output, output2, intermediateFile);

            pass2(intermediateFile, output3);
        } finally {
            intermediateFile.delete();
        }
    }

    /**
     * PASS 1: READ EACH STATEMENT IN FILE AND DETECT TYPE OF STATEMENT
     * IF LABEL: ITS ADDED TO SYMBOL TABLE WITH ITS LOCATION AND CHECK IF ITS NOT DUPLICATED  AND PRINT THE VALUES INSIDE THE SYMBOLTABLE FILE
     * IF COMMENT: THEN ITS IGNORED
     * OPERATION : CHECK THE OPERATION IF AVAILABLE THEN IF NORMAL OPERATION (DEFAULT) THEN IT CHECKS THE FORMAT
     * IT PRINT INSIDE THE LISTING FILE
     * RETURN OBJECT INTO INTERMEDIATE FILE
     *
     * @param input   ASSEMBLY FILE
     * @param output  LISTING FILE
     * @param output2 SYMBOL TABLE
     * @param output3 INTERMEDIATE FILE
     * @throws IOException FILE ERROR
     */

    void pass1(File input, File output, File output2, File output3) throws IOException {

        try (Scanner scanner = new Scanner(input);
             FileOutputStream ostream = new FileOutputStream(output);
             FileOutputStream ostream3 = new FileOutputStream(output3);
             //OBJECT OUTPUT must get from a Serializable class
             //OOS is for writing an object into a file
             ObjectOutputStream objOutputStream = new ObjectOutputStream(ostream3);

             FileOutputStream ostream2 = new FileOutputStream(output2);
             PrintWriter x = new PrintWriter(ostream);
             PrintWriter y = new PrintWriter(ostream2)

        )//all past are parameters for this try block
                //try block:
        {
            location = startAddress = 0;
            firstExecAddress = -1;
            //while not end of file
            while (scanner.hasNext()) {
                try {//try reading lines from file
                    //read each line and parse it into a statement
                    Statement statement = Statement.parse(scanner.nextLine());
                    if (statement.isComment()) {
                        continue;
                    }
                    statement.setLocation(location);

                    //check Duplication of label
                    if (statement.label() != null) {
                        if (symbolTable.containsKey(statement.label())) {
                            throw new Duplicate(statement);

                        } else {
                            symbolTable.put(statement.label(), location);
                            //made it print hexa:
                            y.println(Integer.toHexString(location).toUpperCase() + "\t" + statement.label());

                        }
                    }
                    //check operation
                    switch (statement.operation()) {
                        case "START"://TODO : integer after start is hexa decimal not decimal
                            //K made radix =16 because it is a hexa decimal number
                            startAddress = Integer.parseInt(statement.operand1(),16);

                            statement.setLocation(location = startAddress);
                            break;
                        case "BYTE":
                            String s = statement.operand1();

                            switch (s.charAt(0)) {
                                case 'C':
                                    location += (s.length() - 3); // C'EOF' -> EOF -> 3 bytes
                                    break;
                                case 'X':
                                    location += (s.length() - 3) / 2; // X'05' -> 05 -> 2 half bytes
                                    break;
                            }
                            break;

                        case "WORD":
                            location += 3;

                            break;
                        case "RESW":
                            location += 3 * Integer.parseInt(statement.operand1());

                            break;
                        case "RESB":
                            location += Integer.parseInt(statement.operand1());

                            break;


                        case "END":
                            break;
                        case "BASE":
                            break;

                        //not a directive
                        default:
                            if (opTable.containsKey(statement.operation())) {
                                if (firstExecAddress < 0) {
                                    firstExecAddress = location;
                                }

                                switch (opTable.get(statement.operation()).getFormat()) {
                                    case "1":
                                        location += 1;
                                        break;
                                    case "2":
                                        location += 2;
                                        break;
                                    case "3/4":
                                        //if e=1 then Format 4 then 3+1=4 BYTE
                                        location += 3 + (statement.isExtended() ? 1 : 0);
                                        break;
                                }
                            } else {
                                throw new WrongOperation(statement);
                            }
                    }
                    x.println(statement);
                    objOutputStream.writeObject(statement);
                } catch (Duplicate | WrongOperation e) {
                    x.println(e.getMessage());
                    y.println(e.getMessage());
                }
                Length = location - startAddress;

            }


        }//end try
    }

    /**
     * @param input  READ THE ASSEMBLY FILE
     * @param output THE HTMI FILE
     * @throws IOException
     * @throws ClassNotFoundException
     */
    void pass2(File input, File output) throws IOException, ClassNotFoundException {
        //input file here is intermediate file which is listing file which is one that saves statement objects
        //output file is the HTME file
        try (FileInputStream istream = new FileInputStream(input);
             ObjectInputStream objInputStream = new ObjectInputStream(istream);
             FileWriter objectProgram = new FileWriter(output)

        )

        {

            List<RecordCollector> mRecords = new ArrayList<>();
            TEXT textRecord = new TEXT(startAddress);
            int lastRecordAddress = startAddress;

            while (istream.available() > 0) {
                Statement statement = (Statement) objInputStream.readObject();

                if (statement.isComment()) {
                    continue;
                }
                //compare operation of statement object to "START"
                if (statement.compareTo("START") == 0) {

                    objectProgram.write(new Header(statement.label(), startAddress, Length).toObjectProgram() + '\n');
                } else if (statement.compareTo("END") == 0) {
                    break;
                } else {
                    String objectCode = Instruction(statement);

                    // If it is format 4 and not immediate value
                    if (statement.isExtended() && symbolTable.containsKey(statement.operand1())) {
                        mRecords.add(new Modification(statement.location() + 1, 5));
                    }

//                    Uncomment next line to show the instruction and corresponding object code
//                    System.out.println(statement + "\t\t" + objectCode);

                    if (statement.location() - lastRecordAddress >= 0x1000 || textRecord.add(objectCode) == false) {
                        objectProgram.write(textRecord.toObjectProgram() + '\n');

                        textRecord = new TEXT(statement.location());
                        textRecord.add(objectCode);
                    }

                    lastRecordAddress = statement.location();
                }
            }

            objectProgram.write(textRecord.toObjectProgram() + '\n');

            for (RecordCollector r : mRecords) {
                objectProgram.write(r.toObjectProgram() + '\n');
            }

            objectProgram.write(new END(firstExecAddress).toObjectProgram() + '\n');
        }
    }

    /**
     * @param statement
     * @return
     */
    private String Instruction(Statement statement) {
        String objCode = "";
            //if operation of statement is a valid operation
        if (opTable.containsKey(statement.operation())) {//cases of format of operation
            switch (opTable.get(statement.operation()).getFormat()) {
                case "1":
                    objCode = opTable.get(statement.operation()).getOpcode();

                    break;
                case "2":
                    objCode = opTable.get(statement.operation()).getOpcode();

                    objCode += Integer.toHexString(registerTable.get(statement.operand1())).toUpperCase();
                    objCode += Integer.toHexString(registerTable.get(statement.operand2())).toUpperCase();

                    break;

                case "3/4":
                    final int n = 1 << 5;// n=100000 in binary
                    final int i = 1 << 4;
                    final int x = 1 << 3;
                    final int b = 1 << 2;
                    final int p = 1 << 1;
                    final int e = 1;
                    //The radix parameter is used to specify which numeral system to be used, for example, a radix of 16 (hexadecimal)
                    // indicates that the number in the string should be parsed from a hexadecimal number to a decimal number.
                    int code = Integer.parseInt(opTable.get(statement.operation()).getOpcode(), 16) << 4;
                    String operand = statement.operand1();

                    if (operand == null) {
                        code = (code | n | i) << 12; // for RSUB, NOBASE
                    } else {
                        switch (operand.charAt(0)) {
                            case '#': // immediate addressing
                                code |= i;

                                operand = operand.substring(1);
                                break;
                            case '@': // indirect addressing
                                code |= n;

                                operand = operand.substring(1);
                                break;
                            default: // simple/direct addressing
                                code |= n | i;


                                if (statement.operand2() != null) {
                                    code |= x;
                                }
                        }

                        int disp;
                        //if operand is not a label
                        if (symbolTable.get(operand) == null) {
                           disp = Integer.parseInt(operand);
                        } else {
                            int targetAddress = symbolTable.get(operand);

                            disp = targetAddress;

                            if (statement.isExtended() == false) {
                                disp -= statement.location() + 3;

                                if (disp >= -2048 && disp <= 2047) {
                                    code |= p;
                                } else {
                                    code |= b;
                                    disp = targetAddress - baseAddress;
                                }
                            }
                        }

                        if (statement.isExtended()) {
                            code |= e;

                            code = (code << 20) | (disp & 0xFFFFF);
                        } else {
                            code = (code << 12) | (disp & 0xFFF);
                        }
                    }

                    objCode = String.format(statement.isExtended() ? "%08X" : "%06X", code);

                    break;
            }
        } else if (statement.compareTo("BYTE") == 0) {
            String s = statement.operand1();
            char type = s.charAt(0);

            s = s.substring(s.indexOf('\'') + 1, s.lastIndexOf('\''));

            switch (type) {
                case 'C':
                    for (char ch : s.toCharArray()) {
                        objCode += Integer.toHexString(ch).toUpperCase();
                    }

                    break;
                case 'X':
                    objCode = s;

                    break;
            }
        } else if (statement.compareTo("WORD") == 0) {
            objCode = String.format("%06X", statement.operand1());
        } else if (statement.compareTo("BASE") == 0) {
            baseAddress = symbolTable.get(statement.operand1());
        } else if (statement.compareTo("NOBASE") == 0) {
            baseAddress = 0;
        }

        return objCode;
    }


}

