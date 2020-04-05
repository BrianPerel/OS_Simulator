import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;
/**
 * @author Brian Perel
 * @version 1.0
 *
 * HW# 2
 * Date 02/05/20
 *
 * Hypo Project for OSI course CSCI 465
 *
 * Purpose:
 *	This program will simulate a hypothetical decimal machine
 *  which runs a real-time multitasking operating system (MTOPS),
 *	designed for microcomputers. We will be using the hardware of
 *  the host machine to run the simulated OS. We are building a decimal machine rather than binary.
 *  All hardware is simulated.
 *  Features covered in this project include:
 *  	-scheduling algorithms, memory management,
 *  	-process synchronization, interprocess communication, I/O management, timer management,
 *  	-simulation of a 2-address machine with a stack, event simulation,
 *  	-assembly language programming and hand assembly to machine language program,
 *  	-and an absolute loader.
 *
 *  User programs to test the hardware and operating system are written in assembly language first and then hand assembled into machine language.
 *
 *  HYPO machine program: 1. set all hardware to 0. 2. read executable machine language program from disk txt file (it is the converted assembly program),
 *  3. load from disk file into main memory (done by loader method, loader will stop loading after reading line with negative address),
 *  4. load the PC from the value returned by the absoluteLoader method,
 *  5. call CPU method to execute the program loaded into main memory (execute the loaded instructions),
 *  6. call dump memory method after loading the program and executing it (display the post-execution subset of memory data to console),
 *  Use main method to check all the method return values and take right action
 */
public class Perel_hw2Simulator {

	/* HYPO Machine hardware components global variables (here we are simulating the hardware components by software) */
	static long hypoMainMemory[] = new long[10000]; // main memory array (RAM), size of memory is 10000 words
	static long mar, mbr, clock, IR, psr, pc, sp; // memory addresses
	static long gpr[] = new long[8]; // general purpose registers, 0 to 7

	final static long PROGRAM_HALTED = 1; // halt instruction code
	final static long OK = 0; // status code for successful execution
	final static long END_OF_PROGRAM = -1;

	final static long END_OF_LIST = -1;
	static long RQ = END_OF_LIST; // ready queue
	static long WQ = END_OF_LIST; // waiting queue
	static long OSFreeList = END_OF_LIST;
	static long UserFreeList = END_OF_LIST;
	static long ProcessID = 1;
	static long OSMode = 1;
	static long UserMode = 2;
	static boolean shutdown = false; // flag used to indicate the HYPO Machine should shutdown
	static long systemShutdownStatus; // global shutdown status variable to check in main and exit system
	static long ThisPCB; 
	final static long DEFAULT_PRIORITY = 128;
	final static long READY_STATE = 1;
	final static long TIMESLICE = 200;
	final static long MAX_MEMORY_ADDRESS = 3499; // the highest memory address you can use

	/* HYPO machine error codes, error codes are less than 0, check for errors at every step of OS execution */
	final static long RUN_TIME_ERROR = -2;
	final static long ERROR_FILE_OPEN = -3;
	final static long ERROR_INVALID_ADDRESS = -4;
	final static long ERROR_NO_END_OF_PROGRAM = -5;
	final static long ERROR_INVALID_PC_VALUE = -6;
	final static long ERROR_INVALID_OPCODE_VALUE = -7;
	final static long ERROR_INVALID_GPR_VALUE = -8;
	final static long ERROR_READED_HALT_INSTRUCTION = -9;
	final static long ERROR_INVALID_MODE = -10;
	final static long ERROR_INVALID_MEMORY_ADDRESS = -11;
	final static long ERROR_INVALID_ID = -12;
	final static long ERROR_NO_FREE_MEMORY = -13;
	final static long ERROR_INVALID_MEMORY_SIZE = -14;
	final static long ERROR_INVALID_SIZE_OR_MEMORY_ADDRESS = -15;

	static Scanner scan = new Scanner(System.in); // console input object
    static ArrayList<PCB> PCBs = new ArrayList<PCB>(); // declare array list with PCB class as the type, needed to store the PCBs. For every process we have a different PCB, so use array list to store them dynamically  

	/**
	 * Method name: main
	 *
	 * Task Description:
	 *   calls initializeSystem method,
	 *   (1) reads name of exe file,
	 *   (2) calls loader method to load exe program into main memory,
	 *   (3) load pc from value returned by absoluteLoader method,
	 *   (4) call executeProgram (CPU) method to execute program that was loaded
	 *   into main memory, calls dumpMemory method after loading program
	 *   and executing loaded program
	 *   (5) checks the method return value from each method and takes appropriate action
	 *
	 * Input Parameters:
	 *   @param args: command line arguments
	 *   @throws IOException
	 *
	 * Output Parameters:
	 *   None
	 *
	 * Function return values:
	 *	 note: can't return from main in Java, so instead I put print statements with error
	 *   @return RunTimeError: returns run time error code
	 *   @return ErrorFileOpen: returns file open error code
	 *   @return ErrorInvalidAddress: returns invalid address error code
	 *   @return ErrorNoEndOfProgram: returns no end of program error code
	 *   @return ErrorInvalidPCValue: returns invalid PC value error code
	 *   @return ErrorInvalidOpcodeValue: returns invalid opcode value error code
	 *   @return ErrorInvalidGPRValue: returns invalid GPR value error code
	 *   @return ErrorReachHaltInstruction: returns halt instruction reached error code
	 *   @return OK: returns successful execution code
	 */
	public static void main(String[] args) throws IOException {

		initializeSystem(); // initialize all OS hardware, reset memory when OS starts

		// main loop of HYPO machine
		while(!shutdown) {

			long runningPCB = -1;

			checkAndProcessInterrupt(); // check and process interrupt

			if(shutdown == true) break; // if interrupt is shutdown, terminate program

			dumpMemory("Dynamic memory area before CPU scheduling", 0, 99);

			dispatcher(runningPCB);

			// read HYPO machine language executable filename
			System.out.print("Filename (add .txt to the end): "); // prompt
			String filename = scan.nextLine(); // get file name

			long returnValue = absoluteLoader(filename); // start load method

			// check for return errors from loader
			if(returnValue < 0) {
				System.out.println("Error");
			}
			else {
				pc = returnValue; // PC register gets load function return value
				dumpMemory("Memory dump after loading program", 0, 99);
				long ExecutionCompletionStatus = CPU(); // status variable holds the return status of the CPU. Execute hypo machine program by calling CPU method
				dumpMemory("Memory dump after executing program", 0, 99);

				// check to see if system executed successfully
				if(ExecutionCompletionStatus >= 0)
					System.out.println("OK");
			}
		}

		System.out.println("OS is shutting down");
	}



	/**
	 * Method name: initializeSystem
	 *
	 * Task Description:
	 * 	 Set (initialize) all global system hardware components to 0
	 * 	 Initializes all the HYPO hardware variables to 0 value.
	 * 	 This simulates what happens when you power on your computer:
	 * 	 when OS is loaded all hardware components are reset to 0
	 *
	 * 	 Hardware components: HYPO machine memory (RAM - addresses 0 through memory size minus 1),
	 *   gpr registers (general purpose registers 0 through 7), mar register, mbr register, pc register,
	 *   sp register, psr register, ir register, and system clock
	 *
	 *   mar = Memory address register
	 *   mbr = Memory buffer register
	 *   pc = Program counter register
	 *   sp = Stack pointer register
	 *   psr = Processor status register
	 *   ir = instruction register
	 *
	 * Input Parameters:
	 *   None
	 *
	 * Output Parameters:
	 *   None
	 *
	 * Function return value:
	 *   None
	 * @throws IOException
	 */
	public static void initializeSystem() throws IOException {
		mar = mbr = clock = IR = psr = pc = sp = 0;

		for(int x = 0; x < hypoMainMemory.length; x++) {
			hypoMainMemory[x] = 0;
		}
		for(int x = 0; x < gpr.length; x++) {
			gpr[x] = 0;
		}

		// create user free list using the free block address and size
		UserFreeList = 0;
		long UserFreeBlockPointer = END_OF_LIST;

		// create OS free list using the free block address and size
		OSFreeList = 0;
		long OSFreeBlockPointer = END_OF_LIST;

		String filename = "NullProcessExecutableFile.exe";
		createProcess(filename, 0);
	}



	/**
	 * Method name: absoluteLoader
	 *
	 * Task Description:
	 *   Open the file containing HYPO machine user program
	 *   and load the content into HYPO machine memory,
	 *   If successful load, return the PC value in end of
	 *   program line. Ensure the program file is of proper
	 *   format and that address/PC values are within the
	 *   User Program area. If failure, display appropriate
	 *   error message and return error code.
	 *
	 * Input Parameters:
	 *   @param String filename: String specifying the name of file of machine language program to load. File must exist in project working directory
	 *
	 * Function return values:
	 * 	 @return ErrorFileOpen: returns file open error code
	 * 	 @return ErrorInvalidAddress: returns invalid address error code
	 *   @return ErrorNoEndOfProgram: returns no end of program error code
	 *   @return OK: returns successful load, valid PC value
	 */
	public static long absoluteLoader(String file) throws IOException {

			File fileObj = new File(file); // create file object
			BufferedReader br = new BufferedReader(new FileReader(fileObj)); // create bufferedReader object to read from file

			// load the program from given filename into hypo main memory
			try {

				// if user enters blank (hits enter) for input, print error
				if(file.equals("")) {
					br.close();
					return ERROR_FILE_OPEN;
				}


				String st; // create string to store current line into
				String temp = ""; // temp 1
				String temp2 = ""; // temp 2
				long address = 0; // this variable will store Hypo machine address from file
				long content = 0; // this variable will store memory content

				// access table in file, each line is 1 record, use a delimeter (space) to assign first number in left column of record to address variable and then assign second number to second variable
				// read 1 line of file at a time until null is detected (end of file)
				while((st = br.readLine()) != null)  {

					temp = st.split("\t", 2)[0]; // read from file and split string by the tab, whatever is before goes into variable (read first column of machine code file line)
					temp2 = st.split("\t", 2)[1]; // read from file and split string b the tab, but now whatever is after goes into variable (read second column of machine code file line)
					address = Long.parseLong(temp.trim()); // use trim() to eliminate extra whitespace between address and content values in line from file
					content = Long.parseLong(temp2.trim()); // use trim() to eliminate extra whitespace between address and content values in line from file

					// -1 is not a register address, it is an indicator for end of program. Successful program execution should come to here
					if(address == END_OF_PROGRAM) {
				    	br.close();
				    	return content;
					}

					else if(address >= 0 && address <= 3499) {
						hypoMainMemory[(int) address] = content; // store content into main memory (cast address from type long to int, since indices must be of type int)
					}

					else {
						System.out.println("ErrorInvalidRange");
						br.close();
						return ERROR_INVALID_ADDRESS;
					}
				}
			}

			// file not found exception
			catch(FileNotFoundException e) {
				br.close();
				return ERROR_FILE_OPEN;
			}
			// file IO exception
			catch(IOException e) {
				br.close();
				return ERROR_FILE_OPEN;
			}

			br.close();

			// return error code if program reaches this point since end of program could not be detected
			return ERROR_NO_END_OF_PROGRAM;
	}



	/**
	 * Method name: CPU
	 *
	 * Task Description:
	 *   Method (executes program) performs fetch-decode-execute cycle for
	 *   every given instruction. Simulates the CPU hardware of the OS.
	 *   Performs all possible error checking such as invalid memory address
	 *   reference, invalid mode, division by zero. After execution of
	 *   every instruction, it increases the clock by the instruction execution
	 *   time.
	 *
	 * Input Parameters:
	 *   None
	 *
	 * Output Parameters:
	 *   None
	 *
	 * Function return values
	 *   @return status: returns status value of current CPU execution of program
	 *   @return ErrorInvalidAddress: returns invalid address error code
	 *   @return ErrorInvalidOpcodeValue: returns invalid opcode value error code
	 *   @return RunTimeError: return run time error code
	 */
	public static long CPU() {
		long timeLeft = TIMESLICE;

		final long HALT = 0;
		returnFetchOps recieve; // create variable of class to hold 3 values at once (in object)
		long remainder; // store value after performing remainder operation on IR register in OpCode
		long status = 0; // store and return status of CPU

		// below 5 variables make up a word instruction
		long Opcode; // current instruction
		long Op1Mode; // mode of op1 (of current instruction)
		long Op1GPR; // GPR register number of op1 (of current instruction)
		long Op2Mode; // mode of op2 (of current instruction)
		long Op2GPR; // GPR register number of op2 (of current instruction)

		long Op1Value = 0;
		long Op1Address;
		long Op2Value;
		long Op2Address;
		long result = 0;

		// addressing modes
		long registerMode = 0;
		long immediateMode = 0;

		do {

			// Fetch cycle: fetch (read) first word of instruction pointed by PC
			if(pc >= 0 && pc <= 3499) {
				mar = pc;
				pc++;
				mar = mbr;
			}
			else {
				System.out.println("Invalid address runtime error");
				return ERROR_INVALID_ADDRESS;
			}

			IR = mbr;

			// Decode cycle: decode the first word of instruction into opcode
			Opcode = (int) (IR / 10000);
			remainder = IR % 10000;

			Op1Mode = (int) (remainder / 1000);
			remainder %= 1000;

			Op1GPR = (int) (remainder / 100);
			remainder %= 100;

			Op2Mode = (int) (remainder / 10);
			remainder %= 10;

			Op2GPR = remainder;

			// Opcode max = 12, Op1Mode max = 6, Op1Mode min = 0, Opcode min = 0, Op2Mode max = 6, Op2Mode min = 0
			if(Opcode < 0 || Opcode > 12)
				return ERROR_INVALID_OPCODE_VALUE;

			// check for invalid mode#
			if(Op1Mode >= 0 && Op1Mode <= 6 && Op2Mode >= 0 && Op2Mode <= 6)
				return ERROR_INVALID_MODE;

			///check for invalid GPR#: error = !(0-7)
			if(Op1GPR < 0 && Op1GPR >= 8 && Op2GPR < 0 && Op2GPR >= 8)
				return ERROR_INVALID_GPR_VALUE;

			// Execute cycle: fetch (read) operand values based on opcode
			switch((int) Opcode) { // switch statement cannot evaluate variables of long type, needed to cast
				case 0: { // halt instruction
					System.out.println("halt instruction is encountered");
					clock += 12;
					timeLeft -= 12;
					status = PROGRAM_HALTED;
					return status;
				}

				case 1: { // add instruction
					// pass argument variables to fetchOperand(), method performs appropriate op and returns object, use instance methods to retrieve object values in below variables
					recieve = fetchOperand(Op1Mode, Op1GPR);
					Op1Value = recieve.getOpValue();
					Op1Address = recieve.getOpAddress();
					status = recieve.getStat();

					if(!(status > 0))  {
						System.out.println(status);
						return ERROR_INVALID_ADDRESS;
					}

					// pass argument variables to fetchOperand(), method performs appropriate op and returns object, use instance methods to retrieve object values in below variables
					recieve = fetchOperand(Op2Mode, Op2GPR);
					Op2Value = recieve.getOpValue();
					Op2Address = recieve.getOpAddress();
					status = recieve.getStat();

					// make sure instance variable status from object doesn't return error code value
					if(!(status > 0)) {
						System.out.println(status);
						return ERROR_INVALID_ADDRESS;
					}

					// perform add instruction
					result = Op1Value + Op2Value;

					if(Op1Mode == registerMode)
						gpr[(int) Op1GPR] = result;

					else if(Op1Mode == immediateMode)
						System.out.println("Error destination operand mode cannot be immediate mode");
					else
						result = hypoMainMemory[(int) Op1Address];

					clock++;
					break;
				}

				case 2: { // subtract instruction

					// pass argument variables to fetchOperand(), method performs appropriate op and returns object, use instance methods to retrieve object values in below variables
					recieve = fetchOperand(Op1Mode, Op1GPR);
					Op1Value = recieve.getOpValue();
					Op1Address = recieve.getOpAddress();
					status = recieve.getStat();

					if(!(status > 0))  {
						System.out.println(status);
						return ERROR_INVALID_ADDRESS;
					}

					// pass argument variables to fetchOperand(), method performs appropriate op and returns object, use instance methods to retrieve object values in below variables
					recieve = fetchOperand(Op2Mode, Op2GPR);
					Op2Value = recieve.getOpValue();
					Op2Address = recieve.getOpAddress();
					status = recieve.getStat();

					if(!(status > 0)) {
						System.out.println(status);
						return ERROR_INVALID_ADDRESS;
					}

					result = Op1Value - Op2Value;

					if(Op1Mode == registerMode)
						gpr[(int) Op1GPR] = result;

					else if(Op1Mode == immediateMode)
						System.out.println("Error destination operand mode cannot be immediate mode");
					else
						result = hypoMainMemory[(int) Op1Address];

					clock++;
					break;
				}

				case 3: { // multiply instruction

					// pass argument variables to fetchOperand(), method performs appropriate op and returns object, use instance methods to retrieve object values in below variables
					recieve = fetchOperand(Op1Mode, Op1GPR);
					Op1Value = recieve.getOpValue();
					Op1Address = recieve.getOpAddress();
					status = recieve.getStat();

					if(!(status > 0))  {
						System.out.println(status);
						return ERROR_INVALID_ADDRESS;
					}

					recieve = fetchOperand(Op2Mode, Op2GPR);
					Op2Value = recieve.getOpValue();
					Op2Address = recieve.getOpAddress();
					status = recieve.getStat();

					if(!(status > 0)) {
						System.out.println(status);
						return ERROR_INVALID_ADDRESS;
					}

					result = Op1Value * Op2Value;

					if(Op1Mode == registerMode)
						gpr[(int) Op1GPR] = result;

					else if(Op1Mode == immediateMode)
						System.out.println("Error destination operand mode cannot be immediate mode");
					else
						result = hypoMainMemory[(int) Op1Address];

					clock++;
					break;
				}

				case 4: { // divide instruction
					recieve = fetchOperand(Op1Mode, Op1GPR);
					Op1Value = recieve.getOpValue();
					Op1Address = recieve.getOpAddress();
					status = recieve.getStat();

					if(!(status > 0))  {
						System.out.println(status);
						return ERROR_INVALID_ADDRESS;
					}

					recieve = fetchOperand(Op2Mode, Op2GPR);
					Op2Value = recieve.getOpValue();
					Op2Address = recieve.getOpAddress();
					status = recieve.getStat();

					if(!(status > 0)) {
						System.out.println(status);
						return ERROR_INVALID_ADDRESS;
					}

					// check to make sure division by 0 isn't done
					if(Op2Value == 0) {
						System.out.println("Error! Division by 0");
						return ERROR_INVALID_OPCODE_VALUE;
					}

					result = Op1Value / Op2Value;

					if(Op1Mode == registerMode)
						gpr[(int) Op1GPR] = result;

					else if(Op1Mode == immediateMode)
						System.out.println("Error destination operand mode cannot be immediate mode");
					else
						result = hypoMainMemory[(int) Op1Address];

					clock++;
					break;
				}

				case 5: { // move instruction
					recieve = fetchOperand(Op1Mode, Op1GPR);
					Op1Value = recieve.getOpValue();
					Op1Address = recieve.getOpAddress();
					status = recieve.getStat();

					if(!(status > 0))  {
						System.out.println(status);
						return ERROR_INVALID_ADDRESS;
					}

					recieve = fetchOperand(Op2Mode, Op2GPR);
					Op2Value = recieve.getOpValue();
					Op2Address = recieve.getOpAddress();
					status = recieve.getStat();

					if(!(status > 0)) {
						System.out.println(status);
						return ERROR_INVALID_ADDRESS;
					}

					result = Op2Value;

					if(Op1Mode == registerMode)
						gpr[(int) Op1GPR] = result;

					else if(Op1Mode == immediateMode)
						System.out.println("Error destination operand mode cannot be immediate mode");
					else
						hypoMainMemory[(int) Op1Address] = result;

					clock++;
					break;
				}

				case 6: { // branch instruction
					if(pc >= 0 && pc <= 3499)
						pc = hypoMainMemory[(int) pc];

					else {
						System.out.println("Invalid address range");
						return ERROR_INVALID_ADDRESS;
					}

					break;
				}

				case 7: { // branch on minus instruction
					recieve = fetchOperand(Op1Mode, Op1GPR);
					Op1Value = recieve.getOpValue();
					Op1Address = recieve.getOpAddress();
					status = recieve.getStat();

					if(!(status > 0))  {
						System.out.println(status);
						return ERROR_INVALID_ADDRESS;
					}

					if(Op1Value < 0) {
						if(pc >= 0 && pc <= 3499)
							pc = hypoMainMemory[(int) pc];

						else {
							System.out.println("Error Op1Value invalid range");
							return RUN_TIME_ERROR;
						}
					}
					else {
						pc++;
					}
					break;
				}

				case 8: { // branch on plus instruction
					recieve = fetchOperand(Op1Mode, Op1GPR);
					Op1Value = recieve.getOpValue();
					Op1Address = recieve.getOpAddress();
					status = recieve.getStat();

					if(!(status > 0)) {
						System.out.println(status);
						return ERROR_INVALID_ADDRESS;
					}

					if(Op1Value > 0) {
						if(pc >= 0 && pc <= 3499)
							pc = hypoMainMemory[(int) pc];

						else {
							System.out.println("Error Op1Value invalid range");
							return RUN_TIME_ERROR;
						}
					}
					else {
						pc++;
					}
					break;
				}

				case 9: { // branch on zero instruction
					recieve = fetchOperand(Op1Mode, Op1GPR);
					Op1Value = recieve.getOpValue();
					Op1Address = recieve.getOpAddress();
					status = recieve.getStat();

					if(!(status > 0)) {
						System.out.println(status);
						return ERROR_INVALID_ADDRESS;
					}

					if(Op1Value == 0) {
						if(pc >= 0 && pc <= 3499)
							pc = hypoMainMemory[(int) pc];

						else {
							System.out.println("Error Op1Value invalid range");
							return RUN_TIME_ERROR;
						}
					}
					else {
						pc++;
					}
					break;
				}

				case 10: { // push instruction
					recieve = fetchOperand(Op1Mode, Op1GPR);
					Op1Value = recieve.getOpValue();
					Op1Address = recieve.getOpAddress();
					status = recieve.getStat();

					if(!(status > 0))  {
						System.out.println(status);
						return ERROR_INVALID_ADDRESS;
					}

					sp++;
					hypoMainMemory[(int) sp] = Op1Value;
					break;
				}

				case 11: { // pop instruction
					recieve = fetchOperand(Op1Mode, Op1GPR);
					Op1Value = recieve.getOpValue();
					Op1Address = recieve.getOpAddress();
					status = recieve.getStat();

					if(!(status > 0))  {
						System.out.println(status);
						return ERROR_INVALID_ADDRESS;
					}

					Op1Value = hypoMainMemory[(int) sp];
					sp--;
					break;
				}

				case 12: { // system call instruction

					// check if pc value is in invalid range
					if(pc <= 0 && pc >= 3499) {
						System.out.println(ERROR_INVALID_PC_VALUE);
						return ERROR_INVALID_PC_VALUE;
					}

					long systemCallID = hypoMainMemory[(int) pc++];

					status = systemCall(Op1Value);

					clock += 12;
					timeLeft -= 12;

					break;
				}

				default: {
					System.out.println("Invalid opcode");
					return ERROR_INVALID_OPCODE_VALUE;
				}
			}

		} while(Opcode != HALT && timeLeft > 0); // loop until 0 received indicating halt operation

		return status;
	}



	/**
	 * Method name: FetchOperand
	 *
	 * Task Description:
	 *
	 * Input Parameters:
	 *   Op1Mode		Operand Mode value
	 *   Op1GPR 	    Operand GPR value
	 *
	 * Output Parameters:
	 * 	 OpAddress		Address of operand
	 * 	 OpValue		Operand value when mode and GPR are valid
	 *
	 * Function return values:
	 *   @return returnFetchOps - Object that returns 3 things: status, OpValue, OpAddress
	 */
	public static returnFetchOps fetchOperand(long OpMode, long OpGPR) {
		long OpAddress = 0;
		long OpValue = 0;
		long stat = 0;

		switch((int) OpMode) {
			case 1: // register mode
				OpAddress = -1;
				OpValue = gpr[(int) OpGPR];
				stat = OK;
				break;

			case 2: // register deferred mode -> Op address is in GPR and value in memory
				OpAddress = gpr[(int) OpGPR];

				if(OpAddress >= 0 && OpAddress <= 3499) {
					OpValue = hypoMainMemory[(int) OpAddress];
				}
				else {
					System.out.println("Invalid Address Error");
					stat = ERROR_INVALID_ADDRESS;
				}
				break;

			case 3: // Auto-increment mode - Op address in GPR and Op value in memory
				OpAddress = gpr[(int) OpGPR];
				if(OpAddress >= 0 && OpAddress <= 3499) {
					OpValue = hypoMainMemory[(int) OpAddress];
				}
				else {
					System.out.println("Invalid Address Error");
					stat = ERROR_INVALID_ADDRESS;
				}
				gpr[(int) OpGPR]++;
				break;

			case 4: // Auto-decrement mode
				--gpr[(int) OpGPR];
				OpAddress = gpr[(int) OpGPR];
				if(OpAddress >= 0 && OpAddress <= 3499) {
					OpValue = hypoMainMemory[(int) OpAddress];
				}
				else {
					System.out.println("Invalid Address Error");
					stat = ERROR_INVALID_ADDRESS;
				}
				break;

			case 5: // direct mode - Op address is in the instruction pointed by PC
				if(pc <= 0 && pc >= 3499) {
					OpAddress = hypoMainMemory[(int) pc++];
				}
				OpAddress = hypoMainMemory[(int) pc++];
				if(OpAddress >= 0 && OpAddress <= 3499) {
					OpValue = hypoMainMemory[(int) OpAddress];
				}
				else {
					System.out.println("Invalid Address Error");
					stat = ERROR_INVALID_ADDRESS;
				}
				break;

			case 6: // Immediate mode - operand value is in the instruction
				if(pc <= 0 && pc >= 3499) {
					OpAddress = -1;
					OpValue = hypoMainMemory[(int) pc++];
				}
				else {
					System.out.println("Invalid Address Error");
					stat = ERROR_INVALID_ADDRESS;
				}

				break;

			default: // Invalid mode
				System.out.println("Invalid Error Message");
				stat = ERROR_INVALID_MODE;
				break;
		}

		returnFetchOps returning = new returnFetchOps(stat, OpAddress, OpValue);
		return returning;
	}



	/**
	 * Method name: DumpMemory
	 *
	 * Task Description:
	 *  Displays the content of the Hypo machine GPRs, the clock,
	 *  content of given memory locations, and the given string
	 *
	 * Input Parameters:
	 *   String			String to be displayed
	 *   StartAddress   Start address of memory location
	 *   Size		    Number of locations to dump
	 *
	 * Output Parameters:
	 * 	 None
	 *
	 * Function return values:
	 *   None
	 */
	public static void dumpMemory(String string, long startAddress, long size) {

		System.out.println("\n" + string); // display input parameter String

		long endAddress = startAddress + size;

		if((startAddress < 0 && startAddress > 9999) || (endAddress < 0 && endAddress > 1000) || (size < 0 || size > hypoMainMemory.length))
			System.out.println(ERROR_INVALID_ADDRESS);

		// Print GPR row title
		System.out.println("GPRs:\t G0\tG1\tG2\tG3\tG4\tG5\tG6\tG7\tSP\tPC");

		// Print GPR values
		for(int x = 0; x < gpr.length; x++) {
			System.out.print("\t " + gpr[x]);
		}

		System.out.printf("\t%d\t%d", sp, pc);

		// Print memory column headers
		System.out.print("\nAddress: +0\t+1\t+2\t+3\t+4\t+5\t+6\t+7\t+8\t+9");

		long addr = startAddress;

		// Print memory values
		while(addr < endAddress) {
			System.out.println(addr + "\t");

			for(int i = 1; i < 10; i++) {
				if(addr < endAddress) {
					System.out.print("\t " + hypoMainMemory[(int) addr++]);
				}
				else break;
			}
		}

		System.out.println("\n\nClock: " + clock); // display clock information
		System.out.print("PSR: " + psr + "\n"); // display psr register information
	}



	/**
	 * Method name: create process
	 *
	 * Task Description: Method creates the process for the program and prepares the PCB block (object)
	 *
	 * Input Parameters:
	 *  filename, priority
	 *
	 * Output Parameters:
	 *  None
	 *
	 * Function return values:
	 * @param filename: program used by the absolute loader 
	 * @param priority: indicates which process should be handled first 
	 * @return OK: returns successful execution code 
	 * @throws IOException
	 */
	public static long createProcess(String filename, long priority) throws IOException {
		// Allocate space for Process Control Block
		long PCBptr = allocateOSMemory(1); // change argument later, 0 gives error, 1 works

		ThisPCB = initializePCB(PCBptr); // call initialize PCB ptr and set it to a variable (so we know which PCB we're working with)
		
		// check for error and return error code if memory allocation failed
		if(PCBptr == ERROR_INVALID_MEMORY_SIZE) return ERROR_INVALID_MEMORY_SIZE;

		// load the program
		long value = absoluteLoader(filename);
		if(value == ERROR_FILE_OPEN) return ERROR_FILE_OPEN; // check for program loading error
		PCBs.get((int) ThisPCB).setPC(value);  // store PC value in the PCB of the process

		// Allocate stack space from user free list, allocate user memory of size stack size 
		long ptr = allocateOSMemory(PCBs.get((int) ThisPCB).getStackSize());

		// check for error
		if(ptr < 0) {
			// User memory allocation failed
			freeOSMemory(ptr, value);
			return ptr;
		}

		long stackSize = PCBs.get((int) ThisPCB).getStackSize();
		
		// set SP in the PCB = ptr + stack size
		PCBs.get((int) ThisPCB).setSP(ptr + PCBs.get((int) ThisPCB).getStackSize());
		PCBs.get((int) ThisPCB).setStackStartAddress(ptr);
		PCBs.get((int) ThisPCB).setStackSize(stackSize);

		// set priority in the PCB to priority
		PCBs.get((int) ThisPCB).setPriority(DEFAULT_PRIORITY);

		dumpMemory("Memory dump after creating process", 0, 99);

		printPCB(PCBptr); // print PCB passing PCBptr

		insertIntoRQ(PCBptr); // insert PCB into ready queue passing PCBptr

		return OK;
	}



	/**
	 * Method Name: initializePCB
	 *
	 * Description: PCB (Process Control Block) is related to process - anything that calls create process will deal with PCB such as initializeSystem function
	 * make PCB an object (constructor). Method is used to initialize a new PCB node. The new PCB will start at the specified address and have the specified PID.
	 * It's priority will be set to the default priority of 128 and all other values are initialized to value of 0.
	 *
	 * @param PCBptr: The pointer of the PCB block used to identify it in memory and in queues
	 */
	public static long initializePCB(long PCBptr) {
		long[] PCB_GPR = new long[8]; // declare PCB GPR array, by default an arrays values are all set to 0 
		
		PCB thisPCB = new PCB(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, PCB_GPR, 0, 0, 0); // allocate OS memory for PCB (create an instance)

		// PID of value zero is invalid, since process id's value is going into pid method we check process id value
		if(ProcessID == 0) {
			System.out.println(ERROR_INVALID_ADDRESS);
		}
		thisPCB.setPID(ProcessID++); // allocate PID and set it in the PCB

		thisPCB.setPriority(DEFAULT_PRIORITY); // set priority field in the PCB to default priority
		thisPCB.setState(READY_STATE); // set state field in the PCB equal to ready state
		thisPCB.setNextPCBPointer(END_OF_LIST); // set next PCB pointer field (next pointer in the list)  in the PCB to end of list

		PCBs.add(thisPCB); // add current PCB to PCBs list (array list) for later use in methods 
		
		long PCBIndex = PCBs.indexOf(thisPCB); // create variable that will hold the index value of current PCB 
		return PCBIndex; // return PCBIndex to createProcess() when it calls initializePCB at beginning of method, assign this value to ThisPCB variable 
	}



	/**
	 * Method Name: printPCB
	 *
	 * Print values of all the fields in the PCB
	 *
	 * @param PCBptr
	 */
	public static void printPCB(long PCBptr) {

		System.out.println("PCB address = " + PCBs.get((int) ThisPCB).getPCBptr() +
				"\nNext PCB ptr = " + PCBs.get((int) ThisPCB).getNextPCBPointer() +
				"\nPID = " + PCBs.get((int) ThisPCB).getPID() +
				"\nState = " + PCBs.get((int) ThisPCB).getState() +
				"\nPC = " + PCBs.get((int) ThisPCB).getPC() +
				"\nSP = " + PCBs.get((int) ThisPCB).getSP() +
				"\nPriority = " + PCBs.get((int) ThisPCB).getPriority() +
				"\nStack info: start address = " + PCBs.get((int) ThisPCB).getStackStartAddress() +
				" , size = " + PCBs.get((int) ThisPCB).getStackSize());

				// print 8 GPR values: GPRs = print 8 values of GPR 0 to GPR 7
				System.out.println("GPRs = ");
				long[] gprArr = PCBs.get((int) ThisPCB).getGPR();
				for(int x = 0; x < 8; x++)
					System.out.print(gprArr[x] + " ");
	}



	/**
	 * Print given queue = queue can be ready queue or waiting queue
	 * Walk through the queue from the given pointer until the end of list
	 *
	 * @param Qptr
	 * @return
	 */
	public static long printQueue(long Qptr) {

		long currentPCBptr = Qptr;

		if(currentPCBptr == END_OF_LIST) {
			System.out.println("Empty List");
			return OK;
		}

		// walk through the queue
		while(currentPCBptr != END_OF_LIST) {
			System.out.println("PCB passing current PCB pointer");
			currentPCBptr = currentPCBptr++;
		}

		return OK;
	}



	/**
	 * insertIntoRQ
	 *
	 * The ready queue is an ordered list. The first PCB in the queue has the highest priority.
	 * Hence it will get the CPU next when CPU scheduling is done. Keeping RQ as an ordered linked list
	 * will avoid having to search the list for the highest priority process that should get the CPU.
	 * Therefore, insert the given PCB according to the CPU scheduling algorithm (Priority Round Robin Algorithm).
	 * The scheduling algorithm is implemented at the time of inserting the ready PCB into the RQ.
	 *
	 * @param PCBptr
	 * @return OK 
	 */
	public static long insertIntoRQ(long PCBptr) {
		long previousPtr = END_OF_LIST;
		long currentPtr = RQ;

		// check for valid PCB memory address
		if(PCBptr < 0 || PCBptr > MAX_MEMORY_ADDRESS) {
			System.out.println(ERROR_INVALID_ADDRESS);
			return ERROR_INVALID_MEMORY_ADDRESS;
		}

		// below 3 lines set state to ready state 
		PCBs.get((int) ThisPCB).setState(1);
		long stateIndex = PCBs.get((int) ThisPCB).getState();
		
		hypoMainMemory[(int) (PCBptr + stateIndex)] = READY_STATE; // set state to ready state
		hypoMainMemory[()]

		// if RQ is empty
		if(RQ == END_OF_LIST) {
			RQ = PCBptr;
			return OK;
		}

		// Walk through RQ and find the place to insert. PCB will be inserted at the end of its priority
		while(currentPtr != END_OF_LIST) {

		}

		return OK;
	}



	public static long insertIntoWQ(long PCBptr) {

		if(PCBptr < 0 || PCBptr > MAX_MEMORY_ADDRESS) {
			System.out.println(ERROR_INVALID_MEMORY_ADDRESS);
			return ERROR_INVALID_MEMORY_ADDRESS;
		}

		WQ = PCBptr;

		return OK;
	}



	public static long selectProcessFromRQ() {
		long PCBptr = RQ; // first entry in RQ
		if(RQ != END_OF_LIST) {
			// remove first PCB from RQ
		}

		return PCBptr;
	}



	/**
	 * Method name: saveContext
	 *
	 * Description: Save CPU context into running process PCB.
	 * Running process is going to lose the CPU. Hence, its CPU context has to be
	 * saved in its PCB so that it can be restored when it gets the CPU at a later time.
	 * CPU context consists of GPRs, SP, PC, and PSR.
	 * Method used to store the specified value into the PCB who's start address is startAddress at this index's address.
	 *
	 * @param PCBptr
	 */
	public static void saveContext(long PCBptr) {
	}
	public static void dispatcher(long PCBptr) {
		psr = UserMode; // user mode is 2, set system mode to user mode
	}
	public static void terminateProcess(long PCBptr) {
	}
	public static long allocateOSMemory(long RequestedSize) {
		// Allocate memory from OS free space organized as a link

		// ensure OS free memory exists
		if(OSFreeList == END_OF_LIST) {
			System.out.println(ERROR_NO_FREE_MEMORY);
			return ERROR_NO_FREE_MEMORY;
		}

		if(RequestedSize < 0) {
			System.out.println(ERROR_INVALID_MEMORY_SIZE);
			return ERROR_INVALID_MEMORY_SIZE;
		}

		if(RequestedSize == 1) {
			// minimum allocated memory is 2 locations
			RequestedSize = 2;
		}

		long currentPtr = OSFreeList;
		long previousPtr = END_OF_LIST;

		while(currentPtr != END_OF_LIST) {
			// check each block in the linked list until block with requested memory size is found
			if(hypoMainMemory[(int) (currentPtr + 1)] == RequestedSize) {
				// if block found with requested size, adjust pointers
				if(currentPtr == OSFreeList) {
				OSFreeList = hypoMainMemory[(int) currentPtr];
				hypoMainMemory[(int) currentPtr] = END_OF_LIST;
				return currentPtr; // return memory address
				}
				// not first block
				else {
					hypoMainMemory[(int) previousPtr] = hypoMainMemory[(int) currentPtr]; // point to next block
					hypoMainMemory[(int) currentPtr] = END_OF_LIST; // reset next pointer in the allocated block
					return currentPtr; // return memory address
				}
			}

			// if block found with size greater than requested size
			else if(hypoMainMemory[(int) currentPtr + 1] > RequestedSize) {
				// first block
				if(currentPtr == OSFreeList) {
					hypoMainMemory[(int) (currentPtr + RequestedSize)] = hypoMainMemory[(int) currentPtr]; // move to next block pointer
					hypoMainMemory[(int) (currentPtr + RequestedSize + 1)] = hypoMainMemory[(int) currentPtr + 1] - RequestedSize;
					OSFreeList = currentPtr + RequestedSize; // address of reduced block
					hypoMainMemory[(int) currentPtr] = END_OF_LIST; // reset next pointer in the allocated block
					return currentPtr;
				}
				// not first block
				else {
					hypoMainMemory[(int) (currentPtr + RequestedSize)] = hypoMainMemory[(int) currentPtr]; // move to next block pointer
					hypoMainMemory[(int) (currentPtr + RequestedSize + 1)] = hypoMainMemory[(int) currentPtr + 1] - RequestedSize;
					hypoMainMemory[(int) previousPtr] = currentPtr + RequestedSize; // address of reduced block
					hypoMainMemory[(int) currentPtr] = END_OF_LIST; // reset next pointer in the allocated block
					return currentPtr; // return memory address
				}
			}

			// small block
			else {
				// look at the next block
				previousPtr = currentPtr;
				currentPtr = hypoMainMemory[(int) currentPtr];
			}
		}

		System.out.println(ERROR_NO_FREE_MEMORY);
		return ERROR_NO_FREE_MEMORY;
	}
	public static long freeOSMemory(long ptr, long size) {

		/* if(ptr > ) {
		 		System.out.println(ERROR_INVALID_MEMORY_ADDRESS);
		 		return ERROR_INVALID_MEMORY_ADDRESS;
		   } */

		// check for minimum allocated size, which is 2 even if user asks for 1 location
		if(size == 1) {
			size = 2; // minimum allocated size
		}

		else if(size < 1 || (ptr + size) >= MAX_MEMORY_ADDRESS) {
			System.out.println(ERROR_INVALID_SIZE_OR_MEMORY_ADDRESS);
			return ERROR_INVALID_SIZE_OR_MEMORY_ADDRESS;
		}

		return OK;
	}
	public static long allocateUserMemory(long size) {
		return 0;
	}
	public static long freeUserMemory(long ptr, long size) {

	 /* if(ptr > ) {
 		System.out.println(ERROR_INVALID_MEMORY_ADDRESS);
 		return ERROR_INVALID_MEMORY_ADDRESS;
   		} */

		// check for minimum allocated size, which is 2 even if user asks for 1 location
		if(size == 1) {
			size = 2; // minimum allocated size
		}

		else if(size < 1 || (ptr + size) >= MAX_MEMORY_ADDRESS) {
			System.out.println(ERROR_INVALID_SIZE_OR_MEMORY_ADDRESS);
			return ERROR_INVALID_SIZE_OR_MEMORY_ADDRESS;
		}

		return OK;
	}



	/**
	 * Method name: checkAndProcessInterrupt
	 *
	 * Description: Read interrupt ID number. Based on the interrupt ID, service the interrupt
	 * @throws IOException
	 */
	public static void checkAndProcessInterrupt() throws IOException {

		// prompt possible interrupts selection menu
		System.out.println("\n***********************************************"
							+ "\nPossible Interrupts: \n\t0 - no interrupt"
							+ "\n\t1 - run program\n\t2 - shutdwon system\n\t"
							+ "3 - input operation completion(io_getc)\n\t"
							+ "4 - output operation completion(io_putc)\n"
							+ "\n***********************************************");
		System.out.print("Please choose an interrupt number: ");


		// read interrupt ID
		int interruptID = scan.nextInt();
		System.out.println("Interrupt ID: " + interruptID);

		// system process's interrupt given
		switch(interruptID) {
			case 0: break; // no interrupt

			case 1: isrRunProgramInterrupt(); // run program
					break;

			case 2: isrShutdownSystem(); // shutdown system
					break;

			case 3: isrInputCompletionInterrupt(); // input operation completion - io_getc
					break;

			case 4: isrOutputCompletionInterrupt(); // output operation completion - io_putc
					break;

			default: System.out.println(ERROR_INVALID_ID); // invalid interrupt ID
					 break;
		}
	}



	/**
	 * Method name: isrRunProgramInterrupt
	 *
	 * Description: Run program interrupt service routine (ISR).
	 * Read filename and create process
	 * @throws IOException
	 */
	public static void isrRunProgramInterrupt() throws IOException {

		// prompt and read filename
		System.out.println("Filename (add .txt to the end): ");
		String filename = scan.nextLine();

		// create the process
		createProcess(filename, DEFAULT_PRIORITY);
	}

	public static void isrInputCompletionInterrupt() {
		System.out.println("Enter PID of the process completing input completion interrupt");
		int interrupt = scan.nextInt();
	}

	public static void isrOutputCompletionInterrupt() {
		System.out.println("Enter PID of the process completing input completion interrupt");
		int interrupt = scan.nextInt();
	}

	/**
	 * Method name: isrShutdownSystem
	 *
	 * Description: terminate all processes in RQ and WQ and exit from the program.
	 * This is the only place that the operating system program should exit.
	 */
	public static void isrShutdownSystem() {

		// terminate all processes in RQ one by one
		long ptr = RQ; // set ptr to first PCB pointed by RQ

		while(ptr != END_OF_LIST) {
			RQ = ptr++; // RQ is set to next PCB using ptr
			terminateProcess(ptr); // terminate process
			ptr = RQ;
		}

		// terminate all processes in WQ one by one
		ptr = WQ;

		while(ptr != END_OF_LIST) {
			WQ = ptr++; // RQ is set to next PCB using ptr
			terminateProcess(ptr); // terminate process
			ptr = WQ;
		}
	}

	/**
	 * Method name: searchAndRemovePCBfromWQ
	 *
	 * Description: Search the WQ for the matching pid.
	 *  When a match is found remove it from WQ and return PCB pointer.
	 *  If no match is found, return invalid pid error code.
	 *
	 * @param PID
	 * @return
	 */
	public static long searchAndRemovePCBfromWQ(long PID) {
		long currentPCBptr = WQ;
		long previousPCBptr = END_OF_LIST;

		/* Search WQ for a PCB that has the given pid,
		   if a match is found, remove it from WQ and
		   return the PCB pointer */

		while(currentPCBptr != END_OF_LIST) {
		/*	if(hypoMainMemory[(int) (PCBptr + pidIndex)] == PID) {
				// first PCB
				WQ = hypoMainMemory[(int) (currentPCBptr + nextPCBIndex)];
			}
			else {
				// not first PCB
				hypoMainMemory[(int) (previousPCBptr + nextPCBIndex)] = hypoMainMemory[(int) (previousPCBptr + nextPCBIndex)];
			}

			hypoMainMemory[(int) (currentPCBptr + nextPCBIndex)] = END_OF_LIST; */
			return currentPCBptr;
		}

		// No matchine PCB is found, display pid message and return end of list code
		System.out.println("PID not found");

		return END_OF_LIST;
	}



	/**
	 * Method name: systemCall
	 *
	 * @param systemCallID
	 * @return
	 */
	public static long systemCall(long systemCallID) {

		psr = OSMode;
		long status = OK;

		switch((int) systemCallID) {

		// create process = user process is creating a child process
		case 1: System.out.println("Create process system call not implemented");
				break;

		// delete process
		case 2: System.out.println("Delete process system call not implemented");
				break;

		// process inquiry
		case 3: System.out.println("Process inquery system call not implemented");
				break;

		// dynamic memory allocation: allocate user free memory system call
		case 4: status = memAllocSystemCall();
				break;

		// free dynamically allocated user memory system call
		case 5: status = memFreeSystemCall();
				break;

		// invalid system call ID
		default: System.out.println("Invalid system call ID error");
				 break;
		}

		psr = UserMode;

		return status;
	}
	public static long memAllocSystemCall() {
		long size = gpr[2];

		// check size of 1 and change it to 2
		if(size == 1) {
			size = 2;
		}

		if(gpr[1] < 0) {
			gpr[0] = gpr[1]; // set gpr0 to have the return status
		}
		else {
			gpr[0] = OK;
		}

		System.out.println(memAllocSystemCall() + "\n" + gpr[0] + gpr[1] + gpr[2] + "\n");

		return gpr[0];
	}
	public static long memFreeSystemCall() {
		long size = gpr[2];

		// check size of 1 and change it to 2
		if(size == 1) {
			size = 2;
		}

		gpr[0] = freeUserMemory(gpr[1], size);

		System.out.println(memFreeSystemCall() + "\n" + gpr[0] + gpr[1] + gpr[2] + "\n");

		return gpr[0];
	}
	public static long io_getcSystemCall() {
		return 0;
	}
	public static long io_putcSystemCall() {
		return 0;
	}
}




/**
 * Class Name: PCB
 *
 * Description: Class to contain all information on PCB.
 * Class represents a process' program control block (PCB).
 * PCB is located in hypo machine's OS dynamic memory area.
 *
 * */
class PCB {

	public long PCBptr; // PCB start address (will be used as arg in many methods) 
	public long nextPCBPointer;
	public long PID;
	public long state;
	public long reasonForWaitingCode;
	public long priority;
	public long stackStartAddress;
	public long stackSize;
	public long messageQueueStartAddress;
	public long messageQueueSize;
	public long numOfMessagesInQueue;
	public long GPR[] = new long[8];
	public long SP;
	public long PC;
	public long PSR;
	
	public PCB(long PCBptr, long nextPCBPointer, long PID, long state, long reasonForWaitingCode, long priority, long stackStartAddress, long stackSize, long messageQueueStartAddress, long messageQueueSize, long numOfMessagesInQueue, long[] GPR, long SP, long PC, long PSR) {
		this.PCBptr = PCBptr;
		this.nextPCBPointer = nextPCBPointer;
		this.PID = PID;
		this.state = state;
		this.reasonForWaitingCode = reasonForWaitingCode;
		this.priority = priority;
		this.stackStartAddress = stackStartAddress;
		this.stackSize = stackSize;
		this.messageQueueStartAddress = messageQueueStartAddress;
		this.messageQueueSize = messageQueueSize;
		this.numOfMessagesInQueue = numOfMessagesInQueue;
		
		for(int x = 0; x < GPR.length; x++)
			this.GPR[x] = GPR[x];
		
		this.SP = SP;
		this.PC = PC;
		this.PSR = PSR;
	}

	public long getPCBptr() {
		return PCBptr;
	}
	public long getNextPCBPointer() {
		return nextPCBPointer;
	}
	public long getPC() {
		return PC;
	}
	public long getSP() {
		return SP;
	}
	public long getPSR() {
		return PSR;
	}
	public long getState() {
		return state;
	}
	public long getStackSize() {
		return stackSize;
	}
	public long getPID() {
		return PID;
	}
	public long getPriority() {
		return priority;
	}
	public long[] getGPR() {
		return GPR;
	}
	public long getStackStartAddress() {
		return stackStartAddress;
	}

	public void setPC(long PC) {
		this.PC = PC;
	}
	public void setSP(long SP) {
		this.SP = SP;
	}
	public void setPID(long PID) {
		this.PID = PID;
	}
	public void setStackStartAddress(long stackStartAddress) {
		this.stackStartAddress = stackStartAddress;
	}
	public void setPriority(long priority) {
		this.priority = priority;
	}
	public void setState(long state) {
		this.state = state;
	}
	public void setNextPCBPointer(long nextPCBPointer) {
		this.nextPCBPointer = nextPCBPointer;
	}
	public void setReasonForWaitingCode(long reasonForWaitingCode) {
		this.reasonForWaitingCode = reasonForWaitingCode;
	}
	public void setStackSize(long stackSize) {
		this.stackSize = stackSize;
	}
	public void setMessageQueueStartAddress(long messageQueueStartAddress) {
		this.messageQueueStartAddress = messageQueueStartAddress;
	}
	public void setNumOfMessagesInQueue(long numOfMessagesInQueue) {
		this.numOfMessagesInQueue = numOfMessagesInQueue;
	}
	public void setGPR(long[] GPR, long GPRIndex) {
		    // set individual array index address, passing in array (containing values) and index address 
			this.GPR[(int)GPRIndex] = GPR[(int)GPRIndex];
	}
	public void setPSR(long PSR) {
		this.PSR = PSR;
	}
	public void setPCBptr(long PCBptr) {
		this.PCBptr = PCBptr;
	}
}



/* class to return multiple values (status, Op1Address, Op1Value) to CPU at once */
class returnFetchOps {
	long stat;
	long OpAddress;
	long OpValue;

	public returnFetchOps(long stat, long OpAddress, long OpValue) {
		this.OpAddress = OpAddress;
		this.OpValue = OpValue;
		this.stat = stat;
	}
	public long getOpAddress() {
		return OpAddress;
	}
	public long getOpValue() {
		return OpValue;
	}
	public long getStat() {
		return stat;
	}
}