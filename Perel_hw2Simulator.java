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
 * HYPO Project for OSI course CSCI 465
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
	static long hypoMainMemory[] = new long[10000]; // simulation of main memory array (RAM), size of memory is 10000 words
	static long mar, mbr, clock, IR, psr, pc, sp; // simulation of memory addresses
	static long gpr[] = new long[8]; // simulation of general purpose registers, 0 to 7 (size 8)

	final static long PROGRAM_HALTED = 1; // halt instruction code
	final static long OK = 0; // status code for successful execution
	final static long END_OF_PROGRAM = -1; // variable to indicate that end of machine program has been reached

	final static long END_OF_LIST = -1; // variable to indicate that end of OS or User Free List has been encountered
	static long RQ = END_OF_LIST; // ready queue is set to end of list
	static long WQ = END_OF_LIST; // waiting queue is set to end of list
	static long OSFreeList = END_OF_LIST; // set User Free List to empty list
	static long UserFreeList = END_OF_LIST; // set User Free List to empty list
	static long ProcessID = 1; // variable to hold the current process ID, incremented by 1 every time a new process is created
	static long OSMode = 1; // variable to set system mode to OS Mode
	static long UserMode = 2; // variable to set system mode to User Mode
	static boolean shutdown = false; // flag used to indicate the HYPO Machine should shutdown
	static long ThisPCB; // PCB class instance to make a connection between main class and PCB class
	final static long DEFAULT_PRIORITY = 128; // set default priority to middle value in priority range
	final static long READY_STATE = 1; // variable to indicate CPU ready state
	final static long WAITING_STATE = 2; // variable to indicate CPU waiting state
	final static long RUNNING_STATE = 3; // variable to indicate CPU running state
	final static long TIMESLICE = 200; // variable time slice is set to 200 clock ticks
	final static long MAX_MEMORY_ADDRESS = 3499; // the highest memory address you can use
	final static long HALT_IN_PROGRAM_REACHED = 1; // variable used to indicate the CPU() has reached a halt
	final static long TIME_SLICE_EXPIRED = 2; // variable used to indicate that a time slice expiration point was reached
	final static long START_ADDR_OF_OS_FREELIST = 4500; // variable to mark starting address of OS free list
	final static long START_ADDR_OF_USER_PROGRAM_AREA = 0; // variable to hold starting address of the user program area
	final static long END_ADDR_OF_USER_PROGRAM_AREA = 2499; // variable to hold end address of user program area
	final static long START_ADDR_OF_USER_FREELIST = 2500; // variable to mark start address of user free list
	final static long END_ADDR_OF_USER_FREELIST = 4499; // variable to mark end address of user free list
	final static long IO_GETCINTERRUPT = 3; // variable used when "input operation is completed" interrupt is encountered
	final static long IO_PUTCINTERRUPT = 4; // variable used when "output operation is completed" interrupt is encountered
	final static long PCB_SIZE = 12; // Java allocates 12 bytes for every object so PCB size is set to 12
	static long runningPCBptr = END_OF_LIST;
	
	/* HYPO machine error codes, error codes are less than 0, check for errors at every step of OS execution */
	final static long RUN_TIME_ERROR = -2;
	final static long ERROR_FILE_OPEN = -3; // error code to indicate file could not be opened
	final static long ERROR_INVALID_ADDRESS = -4; // error code to indicate invalid address was given
	final static long ERROR_NO_END_OF_PROGRAM = -5; // error code to indicate end of program could not be reached while reading a machine program in absolute loader
	final static long ERROR_INVALID_PC_VALUE = -6; // error code to indicate invalid PC value encountered, outside of allowed range of PC values
	final static long ERROR_INVALID_OPCODE_VALUE = -7; // error code to indicate invalid opcode value encountered, outside of allowed range of opcode values
	final static long ERROR_INVALID_GPR_VALUE = -8; // error code to indicate invalid GPR value encountered, outside of allowed range of GPR values
	final static long ERROR_REACHED_HALT_INSTRUCTION = -9; // error code to indicate halt instruction encountered in CPU() while executing program instructions
	final static long ERROR_INVALID_MODE = -10; // error code to indicate invalid mode encountered
	final static long ERROR_INVALID_MEMORY_ADDRESS = -11; // error code to indicate invalid memory address encountered
	final static long ERROR_INVALID_ID = -12; // error code to indicate invalid ID value encountered
	final static long ERROR_NO_FREE_MEMORY = -13; // error code to indicate no free memory left, all used by program
	final static long ERROR_INVALID_MEMORY_SIZE = -14; // error code to indicate invalid memory size encountered
	final static long ERROR_INVALID_SIZE_OR_MEMORY_ADDRESS = -15; // error code to indicate invalid size or memory address encountered, outside of range
	final static long ERROR_NO_AVAILABLE_MEMORY = -16; // error code to indicate no available memory left
	final static long ERROR_REQUESTED_MEMORY_TOO_SMALL = -17; // error code to indicate requested (input) memory is too small

	static Scanner scan = new Scanner(System.in); // console input object instance
    static ArrayList<PCB> PCBs = new ArrayList<PCB>(); // declare array list with PCB class as the type, needed to store the PCBs. For every process we have a different PCB, so use array list to store them dynamically

	/**
	 * Method Name: main
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
	 * Method Return Values:
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

		System.out.println("System Booting...");

		long status; // variable to hold the statuses of CheckandProcessInterrupt and returning CPU values.

		initializeSystem(); // initialize all OS hardware, reset memory when OS starts

		System.out.println("\nStarting OS...");

		// main loop of HYPO machine runs until shutdown
		while(!shutdown) {

			status = checkAndProcessInterrupt(); // check and process interrupt

			if(status == 2) break; // if interrupt is shutdown, terminate program

			System.out.println("\nRQ: Before CPU scheduling"); // dump the contents of RQ
			printQueue(RQ);

			System.out.println("\nWQ: Before CPU scheduling"); // dump the contents of WQ
			printQueue(WQ);

			dumpMemory("Dynamic memory area before CPU scheduling", 0, 99);

			// select next process from RQ to give CPU
			runningPCBptr = selectProcessFromRQ();

			runningPCBptr = 0; /***************************************** TEMP SOLUTION, REMOVE WHEN READY *****************************************/

			// perform restore context using dispatcher
			dispatcher(runningPCBptr);

			System.out.println("\nRQ: After selecting process from RQ"); // dump the contents of RQ
			printQueue(RQ);

			System.out.println("\nDumping the PCB contents of the running PCB"); // dump the contents of WQ
			printPCB(runningPCBptr);

			// execute instructions of the running process using the CPU
			System.out.println("\n\nExecuting CPU...");
			status = CPU();
			System.out.println("\nCPU execution completed");

			dumpMemory("\nDynamic memory area after executing program", 0, 99);

			if(status == TIME_SLICE_EXPIRED) {
				System.out.println("\nTime slice has expired, saving context and inserting back into RQ");
				saveContext(runningPCBptr); // Save CPU Context of running process in its PCB, because the running process is losing control of the CPU.
				insertIntoRQ(runningPCBptr); // Insert running process PCB into RQ.
				runningPCBptr = END_OF_LIST; // Set the running PCB pointer to the end of list.
			}
			else if(status == HALT_IN_PROGRAM_REACHED || status < 0) {
				System.out.println("\nHalt in program reached, end of program");
				terminateProcess(runningPCBptr);
				runningPCBptr = END_OF_LIST;
			}
			else if(status == io_getcSystemCall()) {
				System.out.println("\nInput Interrupt detected");
				hypoMainMemory[(int) runningPCBptr /* + reasonForWaitingCode*/] = io_putcSystemCall(); //Set reason for waiting in the running PCB to Output Completion Event
				insertIntoWQ(runningPCBptr); // insert running process into WQ.
				runningPCBptr = END_OF_LIST; // set running PCB pointer to end of list
			}
			else if(status == io_putcSystemCall()) {
				System.out.println("\nOutput interrupt detected");
				hypoMainMemory[(int) runningPCBptr /* + reasonForWaitingCode*/] = io_putcSystemCall(); //Set reason for waiting in the running PCB to Output Completion Event
				insertIntoWQ(runningPCBptr); // insert running process into WQ.
				runningPCBptr = END_OF_LIST; // set running PCB pointer to end of list
			}
			else {
				System.out.println("Unkown programming error detected");
			}
		}

		System.out.println("OS is shutting down...\nReturning code: " + OK + "\nGoodbye");
	}



	/**
	 * Method Name: initializeSystem
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
	 * Method Return Values:
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
		UserFreeList = START_ADDR_OF_USER_FREELIST; // set user free list to 2500
		hypoMainMemory[(int) (UserFreeList + 3499)] = END_OF_LIST;
		hypoMainMemory[(int) (UserFreeList + 1)] = START_ADDR_OF_USER_FREELIST;

		// create OS free list using the free block address and size
		OSFreeList = START_ADDR_OF_OS_FREELIST;
		hypoMainMemory[(int) (OSFreeList + 3499)] = END_OF_LIST; // 7999
		hypoMainMemory[(int) (OSFreeList + 1)] = START_ADDR_OF_OS_FREELIST; // 3500

		System.out.print("Hardware units successfully initialized.");

		String filename = "Null.txt";
		createProcess(filename, 0);
	}



	/**
	 * Method Name: absoluteLoader
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
	 * Output Parameters:
	 * 	 None
	 *
	 * Method Return Values:
	 * 	 @return ErrorFileOpen: returns file open error code
	 * 	 @return ErrorInvalidAddress: returns invalid address error code
	 *   @return ErrorNoEndOfProgram: returns no end of program error code
	 *   @return OK: returns successful load, valid PC value
	 */
	public static long absoluteLoader(String file) throws IOException {

			File fileObj = new File(file); // create file object
			BufferedReader br = new BufferedReader(new FileReader(fileObj)); // create bufferedReader object to read from file
			// load the program from given filename into HYPO main memory
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
	 * Method Name: CPU
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
	 * Method Return Values
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
		long Opcode, Op1Mode, Op1GPR, Op2Mode, Op2GPR, Op1Value = 0;
		long Op1Address, Op2Value, Op2Address, result;

		// addressing modes
		long registerMode = 0;
		long immediateMode = 0;

		do {

			// Fetch cycle: fetch (read) first word of instruction pointed by PC
			if(pc >= 0 && pc <= 2499) {
				mar = pc++;
				mbr = hypoMainMemory[(int) mar];
			}
			else {
				System.out.println("Invalid address runtime error. Returning error code: " + ERROR_INVALID_PC_VALUE);
				return ERROR_INVALID_PC_VALUE;
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
			if(Opcode < 0 || Opcode > 12) {
				System.out.println("\nError: Invalid opcode value. Return error code: " + ERROR_INVALID_OPCODE_VALUE);
				return ERROR_INVALID_OPCODE_VALUE;
			}

			// check for invalid mode#
			if(Op1Mode < 0 && Op1Mode > 6 && Op2Mode < 0 && Op2Mode > 6) {
				System.out.println("\nError: Invalid mode found. Return error code: " + ERROR_INVALID_MODE);
				return ERROR_INVALID_MODE;
			}

			///check for invalid GPR#: error = !(0-7)
			if(Op1GPR < 0 || Op1GPR > 7 || Op2GPR < 0 && Op2GPR > 7) {
				System.out.println("\nError: Invalid GPR value found. Return error code: " + ERROR_INVALID_GPR_VALUE);
				return ERROR_INVALID_GPR_VALUE;
			}

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
						return status;
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
						hypoMainMemory[(int) Op1Address] = result;

					clock += 3;
					timeLeft -= 3;
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
						hypoMainMemory[(int) Op1Address] = result;

					clock += 3;
					timeLeft -= 3;
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
						hypoMainMemory[(int) Op1Address] = result;

					clock += 6;
					timeLeft -= 6;
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
						hypoMainMemory[(int) Op1Address] = result;

					clock += 6;
					timeLeft -= 6;
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

					clock += 2;
					timeLeft -= 2;
					break;
				}

				case 6: { // branch instruction
					if(pc >= 0 && pc <= 2499)
						pc = hypoMainMemory[(int) pc];

					else {
						System.out.println("Invalid address value encountered. Returning error code: " + ERROR_INVALID_ADDRESS);
						return ERROR_INVALID_ADDRESS;
					}

					clock += 2;
					timeLeft -= 2;
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
						if(pc >= 0 && pc <= 2499)
							pc = hypoMainMemory[(int) pc];

						else {
							System.out.println("Error invalid PC value encountered. Returning error code: " + ERROR_INVALID_PC_VALUE);
							return ERROR_INVALID_PC_VALUE;
						}
					}
					else pc++;

					clock += 4;
					timeLeft -= 4;
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
						if(pc >= 0 && pc <= 2499)
							pc = hypoMainMemory[(int) pc];

						else {
							System.out.println("Error invalid PC value encountered. Returning error code: " + ERROR_INVALID_PC_VALUE);
							return ERROR_INVALID_PC_VALUE;
						}
					}
					else pc++;

					clock += 4;
					timeLeft -= 4;
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
						if(pc >= 0 && pc <= 2499)
							pc = hypoMainMemory[(int) pc];

						else {
							System.out.println("Error invalid PC value encountered. Returning error code: " + ERROR_INVALID_PC_VALUE);
							return ERROR_INVALID_PC_VALUE;
						}
					}
					else pc++;

					clock += 4;
					timeLeft -= 4;
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

					clock += 2;
					timeLeft -= 2;
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

					clock += 2;
					timeLeft -= 2;
					break;
				}

				case 12: { // system call instruction

					// check if PC value is in invalid range
					if(pc <= 0 && pc >= 2499) {
						System.out.println("Error invalid PC value encountered. Returning error code: " + ERROR_INVALID_PC_VALUE);
						return ERROR_INVALID_PC_VALUE;
					}

					long systemCallID = hypoMainMemory[(int) pc++];

					status = systemCall(Op1Value);

					clock += 12;
					timeLeft -= 12;
					break;
				}

				default: {
					System.out.println("Invalid opcode encountered. Returning error code: " + ERROR_INVALID_OPCODE_VALUE);
					return ERROR_INVALID_OPCODE_VALUE;
				}
			}

		} while(Opcode != HALT && timeLeft > 0); // loop until 0 received indicating halt operation

		return status;
	}



	/**
	 * Method Name: FetchOperand
	 *
	 * Task Description: take in operand mode to determine which case is to be executed
	 *
	 * Input Parameters:
	 *   Op1Mode		Operand Mode value
	 *   Op1GPR 	    Operand GPR value
	 *
	 * Output Parameters:
	 * 	 OpAddress		Address of operand
	 * 	 OpValue		Operand value when mode and GPR are valid
	 *
	 * Method Return Values:
	 *   @return returnFetchOps - Object that returns 3 things: status, OpValue, OpAddress
	 */
	public static returnFetchOps fetchOperand(long OpMode, long OpGPR) {
		long OpAddress = 0;
		long OpValue = 0;
		long stat = 0;

		switch((int) OpMode) {
			case 1: // register mode
				OpAddress = -2;
				OpValue = gpr[(int) OpGPR];
				stat = OK;
				break;

			case 2: // register deferred mode -> Op address is in GPR and value in memory
				OpAddress = gpr[(int) OpGPR];

				if(OpAddress <= END_ADDR_OF_USER_FREELIST && OpAddress >= START_ADDR_OF_USER_FREELIST) {
					OpValue = hypoMainMemory[(int) OpAddress];
				}
				else {
					System.out.println("Error invalid address encountered. Returning error code: " + ERROR_INVALID_ADDRESS);
					stat = ERROR_INVALID_ADDRESS;
				}
				break;

			case 3: // Auto-increment mode - Op address in GPR and Op value in memory
				OpAddress = gpr[(int) OpGPR];
				if(OpAddress <= END_ADDR_OF_USER_FREELIST && OpAddress >= START_ADDR_OF_USER_FREELIST) {
					OpValue = hypoMainMemory[(int) OpAddress];
				}
				else {
					System.out.println("Error invalid address encountered. Returning error code: " + ERROR_INVALID_ADDRESS);
					stat = ERROR_INVALID_ADDRESS;
				}
				gpr[(int) OpGPR]++;
				break;

			case 4: // Auto-decrement mode
				--gpr[(int) OpGPR];
				OpAddress = gpr[(int) OpGPR];
				if(OpAddress <= END_ADDR_OF_USER_FREELIST && OpAddress >= START_ADDR_OF_USER_FREELIST) {
					OpValue = hypoMainMemory[(int) OpAddress];
				}
				else {
					System.out.println("Error invalid address. Returning error code: " + ERROR_INVALID_ADDRESS);
					stat = ERROR_INVALID_ADDRESS;
				}
				break;

			case 5: // direct mode - operand address is in the instruction pointed by PC
				if(pc <= 0 && pc >= 2499) {
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
				if(pc <= 0 && pc >= 2499) {
					OpAddress = -2;
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
	 * Method Name: DumpMemory
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
	 * Method Return Values:
	 *   None
	 */
	public static void dumpMemory(String string, long startAddress, long size) {

		System.out.println("\n" + string + ":\n"); // display input parameter String

		long endAddress = startAddress + size;

		if((startAddress < 0 || startAddress > MAX_MEMORY_ADDRESS) || (endAddress < 0 && endAddress > 1000) || (size < 1 || size > hypoMainMemory.length))
			System.out.println("Invalid start address, end address, or size. Return error code: " + ERROR_INVALID_ADDRESS);

		else {
			// Print GPR row title
			System.out.println("GPRs:\t G0\tG1\tG2\tG3\tG4\tG5\tG6\tG7\tSP\tPC");

			// Print GPR values
			for(int x = 0; x < gpr.length; x++) {
				if(x == 0) {
					System.out.print("\t " + gpr[x]);
				}
				else {
					System.out.print("\t" + gpr[x]);
				}
			}

			System.out.printf("\t%d\t%d%n", sp, pc);

			// Print memory column headers
			System.out.print("\nAddress: +0\t+1\t+2\t+3\t+4\t+5\t+6\t+7\t+8\t+9\n");

			long addr = startAddress;

			// Print memory values
			while(addr <= endAddress) {
				if(addr >= 10) System.out.print("\n" + addr);

				else System.out.print(addr);

				for(int i = 0; i < 10; i++) {
					if(addr <= endAddress) {
						System.out.print("\t " + hypoMainMemory[(int) addr]);
						addr++;
					}
					else break;
				}
			}

			System.out.println("\n\nClock: " + clock); // display clock information
			System.out.print("PSR: " + psr + "\n"); // display PSR register information
		}
	}



	/**
	 * Method Name: create process
	 *
	 * Task Description: Method creates the process for the program, 
	 *  prepares the PCB block (object), initializes all PCB contents to 
	 *  proper values, defines stack space for the program and dumps user 
	 *  program area memory locations 
	 *
	 * Input Parameters:
	 *  filename: filename of machine file for which were creating a process
	 *  priority: the priority value of the process, indicates which process should be handled first 
	 *
	 * Output Parameters:
	 *  None
	 *
	 * Method Return Values:
	 *  @return OK: returns successful execution code
	 *  @throws IOException
	 */
	public static long createProcess(String filename, long priority) throws IOException {

		// Allocate space for PCB
		long PCBptr = allocateOSMemory(PCB_SIZE); // change argument later, 0 gives error, 1 works

		// check return value from allocateOSMemory(), if < 0 then error encountered
		if(PCBptr < 0) {
			return PCBptr;
		}

		ThisPCB = initializePCB(PCBptr); // call initialize PCB pointer and set it to a variable (so we know which PCB we're working with)

		// load the program
		long value = absoluteLoader(filename);

		if(value < 0) {
			return value; // check for program loading error
		}

		PCBs.get((int) ThisPCB).setPC(value);  // store PC value in the PCB of the process

		// Allocate stack space from user free list, allocate user memory of size stack size
		long ptr = allocateUserMemory(PCBs.get((int) ThisPCB).getStackSize());

		// check for error
		if(ptr < 0) {
			// User memory allocation failed
			freeOSMemory(ptr, value);
			return ptr;
		}

		long stackSize = PCBs.get((int) ThisPCB).getStackSize();

		// set SP in the PCB = pointer + stack size
		PCBs.get((int) ThisPCB).setSP(ptr + stackSize);
		PCBs.get((int) ThisPCB).setStackStartAddress(ptr);
		PCBs.get((int) ThisPCB).setStackSize(stackSize);

		// set priority in the PCB to priority
		PCBs.get((int) ThisPCB).setPriority(DEFAULT_PRIORITY);

		dumpMemory("\nDumping memory addresses in user program area", 0, 99);

		printPCB(PCBptr); // print PCB passing PCBptr
		insertIntoRQ(PCBptr); // insert PCB into ready queue passing PCBptr

		return OK;
	}



	/**
	 * Method Name: initializePCB
	 *
	 * Task Description: PCB (Process Control Block) is related to process - anything that calls create process will deal with PCB such as initializeSystem function
	 *  make PCB an object (constructor). Method is used to initialize a new PCB node. The new PCB will start at the specified address and have the specified PID.
	 *  It's priority will be set to the default priority of 128 and all other values are initialized to value of 0.
	 *
	 * Input Parameters: 
	 *  PCBptr: memory location of the PCB, used to identify it in memory and in queues
	 *  
	 * Output Parameters:
	 *  None 
	 *
	 * Method Return Values: 
	 *  None
	 */
	public static long initializePCB(long PCBptr) {
		long[] PCB_GPR = new long[8]; // declare PCB GPR array

		for(int i = 0; i < PCB_GPR.length; i++) {
			PCB_GPR[i] = 0;
		}

		PCB thisPCB = new PCB(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, PCB_GPR, 0, 0, 0); // allocate OS memory for PCB (create an instance)

		// PID of value zero is invalid, since process id's value is going into PID method we check process id value
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
	 * Task Description: 
	 *  Print values of all the fields in the PCB. This includes it's PID, state
	 *  priority, SP, PC, size, next PCB pointer, GPR's, and stack address 
	 *  
	 * Input Parameters: 
	 *  PCBptr: memory location of PCB 
	 *  
	 * Output Parameters:
	 *  None 
	 *  
	 * Method Return Values:
	 *  None 
	 */
	public static void printPCB(long PCBptr) {
		System.out.println("\nContents of the PCB in memory:" +
				"\nPCB address = " + PCBs.get((int) ThisPCB).getPCBptr() +
				", Next PCB ptr = " + PCBs.get((int) ThisPCB).getNextPCBPointer() +
				", PID = " + PCBs.get((int) ThisPCB).getPID() +
				", State = " + PCBs.get((int) ThisPCB).getState() +
				", PC = " + PCBs.get((int) ThisPCB).getPC() +
				", SP = " + PCBs.get((int) ThisPCB).getSP() +
				", Priority = " + PCBs.get((int) ThisPCB).getPriority() +
				", Stack info: start address = " + PCBs.get((int) ThisPCB).getStackStartAddress() +
				" , size = " + PCBs.get((int) ThisPCB).getStackSize());

				// print 8 GPR values: GPRs = print 8 values of GPR 0 to GPR 7
				System.out.print("GPRs:\t");
				long[] gprArr = PCBs.get((int) ThisPCB).getGPR();
				for(int x = 0; x < 8; x++)
					System.out.print("GPR" + x + ": " + gprArr[x] + " ");
	}



	/**
	 * Method Name: printQueue
	 * 
	 * Task Description: 
	 *  Walk through the queue from the given pointer until the end of list
	 *
	 * Input Parameters: 
	 *  @param Qptr
	 *  
	 * Output Parameters: 
	 *  None 
	 *  
	 * Method return values: 
	 *  @return OK: Success code, is returned if queue was printed 
	 */
	public static long printQueue(long Qptr) {

		long currentPCBptr = Qptr;

		if(currentPCBptr == END_OF_LIST) {
			System.out.println("The list is empty");
			return OK;
		}

		// walk through the queue
		while(currentPCBptr != END_OF_LIST) {
			printPCB(currentPCBptr);
			currentPCBptr++;
		}

		return OK;
	}



	/**
	 * Method Name: insertIntoRQ
	 *
	 * Description:
	 *  The ready queue is an ordered list. The first PCB in the queue has the highest priority.
	 *  Hence it will get the CPU next when CPU scheduling is done. Keeping RQ as an ordered linked list
	 *  will avoid having to search the list for the highest priority process that should get the CPU.
	 *  Therefore, insert the given PCB according to the CPU scheduling algorithm (Priority Round Robin Algorithm).
	 *  The scheduling algorithm is implemented at the time of inserting the ready PCB into the RQ.
	 *
	 * Input Parameters: 
	 *  @param PCBptr: memory location of the PCB 
	 *  
	 * Output Parameters: 
	 *  None 
	 *  
	 * Method return values: 
	 *  @return ERROR_INVALID_MEMORY_ADDRESS: Invalid memory address encountered 
	 *  @return OK: success code, if PCB was inserted 
	 */
	public static long insertIntoRQ(long PCBptr) {
		long previousPtr = END_OF_LIST;
		long currentPtr = RQ;

		// check for valid PCB memory address
		if(PCBptr < 0 || PCBptr > MAX_MEMORY_ADDRESS) {
			System.out.println("\nError: Invalid memory range detected. Return error code: " + ERROR_INVALID_ADDRESS);
			return ERROR_INVALID_MEMORY_ADDRESS;
		}

		long stateIndex = PCBs.get((int) ThisPCB).getState();
		hypoMainMemory[(int) (PCBptr + stateIndex)] = READY_STATE; // set state to ready state

		long nextPCBPointerIndex = PCBs.get((int) ThisPCB).getNextPCBPointer();
		hypoMainMemory[(int) (PCBptr + PCBs.get((int) ThisPCB).getNextPCBPointer())] = END_OF_LIST; // set next pointer to end of list

		// if RQ is empty
		if(RQ == END_OF_LIST) {
			RQ = PCBptr;
			return OK;
		}

		// Walk through RQ and find the place to insert. PCB will be inserted at the end of its priority
		while(currentPtr != END_OF_LIST) {
			if(hypoMainMemory[(int) (PCBptr + PCBs.get((int) ThisPCB).getPriority())] > hypoMainMemory[(int) (currentPtr +  PCBs.get((int) ThisPCB).getPriority())]) {
				// found the place to insert
				if(previousPtr == END_OF_LIST) {
					// enter PCB in the front of the list as first entry
					hypoMainMemory[(int) (PCBptr + nextPCBPointerIndex)] = RQ;
					RQ = PCBptr;
					return OK;
				}

				// enter PCB in the middle of the list
				hypoMainMemory[(int) (PCBptr + nextPCBPointerIndex)] = hypoMainMemory[(int) (previousPtr + nextPCBPointerIndex)];
				hypoMainMemory[(int) (previousPtr + nextPCBPointerIndex)] = PCBptr;
				return OK;
			}
			else {  // PCB to be inserted has lower or equal priority to the current PCB in RQ
				// go the the next PCB in RQ
				previousPtr = currentPtr;
				currentPtr = hypoMainMemory[(int) (currentPtr + nextPCBPointerIndex)];
			}
		}

		// insert PCB at the end of the RQ
		hypoMainMemory[(int) (previousPtr + nextPCBPointerIndex)] = PCBptr;
		return OK;
	}



	/**
	 * Method Name: insertIntoWQ
	 * 
	 * Task Description: 
	 *  Take PCBptr and insert it into front of waiting queue after 
	 *  which we adjust the next pointer index value.
	 *  
	 * Input Parameters:
	 *  @param PCBptr: memory location of the PCB 
	 *  
	 * Output Parameters:
	 *  None 
	 * 
	 * Method return values: 
	 *  @return OK: success code, if PCB was inserted into WQ 
	 *  @return ERROR_INVALID_MEMORY_ADDRESS: invalid memory address encountered code 
	 */
	public static long insertIntoWQ(long PCBptr) {
		// insert the given PCB at the front of WQ
		if(PCBptr < 0 || PCBptr > MAX_MEMORY_ADDRESS) {
			System.out.println("\nError: Invalid memory range found. Return error code: " + ERROR_INVALID_MEMORY_ADDRESS);
			return ERROR_INVALID_MEMORY_ADDRESS;
		}

		hypoMainMemory[(int) (PCBptr + PCBs.get((int) ThisPCB).getState())] = WAITING_STATE; // set state to ready state
		hypoMainMemory[(int) (PCBptr + PCBs.get((int) ThisPCB).getNextPCBPointer())] = WQ; // set next pointer to end of list

		WQ = PCBptr;

		return OK;
	}



	/**
	 * Method Name: selectProcessFromRQ
	 *
	 * Task Description: 
	 *  Select first process from RQ to give CPU. When CPU has to be allocated to the next process in RQ, select the first process
	 *  in the RQ and return the pointer to the PCB since processes in RQ are already ordered from highest to lowest priority
	 *
	 * Input Parameters: 
	 *  None 
	 *  
	 * Output Parameter:
	 *  None 
	 *
	 * Method Return Values: 
	 *  @return PCBptr: PCB address of the first PCB in the ready queue 
	 */
	public static long selectProcessFromRQ() {
		long PCBptr = RQ; // first entry in RQ
		//	PCBs.get((int) ThisPCB).setPCBptr(PCBptr); // update the PCBptr field in the class too with above value

		if(RQ != END_OF_LIST) {
			RQ = PCBs.get((int) ThisPCB).getNextPCBPointer(); // remove first PCB from RQ, set RQ to next PCB pointed by RQ
		}

		PCBs.get((int) ThisPCB).setNextPCBPointer(END_OF_LIST); // set next point to END_OF_LIST in the PCB; set next PCB field in the given PCB to END_OF_LIST

		return PCBptr;
	}



	/**
	 * Method Name: saveContext
	 *
	 * Task Description:
	 *  Save CPU context into running process PCB.
	 *  Running process is going to lose the CPU. Hence, its CPU context has to be
	 *  saved in its PCB so that it can be restored when it gets the CPU at a later time.
	 *  CPU context consists of GPRs, SP, PC, and PSR.
	 *  Method used to store the specified value into the PCB who's 
	 *  start address is startAddress at this index's address.
	 *
	 * Input Parameters: 
	 *  @param PCBptr: memory location of the PCB 
	 *  
	 * Output Parameters:
	 *  None 
	 *  
	 * Method Return Values: 
	 *  None 
	 */
	public static void saveContext(long PCBptr) {
		// copy all CPU GPRs, SP, PC, and PSR values into PCB using PCBptr

		long[] PCBgpr = PCBs.get((int) ThisPCB).getGPR();

		// create a temporary PCB GPR array and copy to it all values from CPU GPR array
		for(int i = 0; i < 8; i++) {
			PCBgpr[i] = gpr[i];
			PCBs.get((int) PCBptr).setGPR(PCBgpr, gpr[i]);
		}

		PCBs.get((int) PCBptr).setSP(sp);
		PCBs.get((int) PCBptr).setPC(pc);
		PCBs.get((int) PCBptr).setPSR(psr);
	}



	/**
	 * Method Name: dispatcher
	 * 
	 * Task Description: 
	 *  Take PCBptr and store its context to all hardware components. 
	 *  Copy CPU GPR register values from given PCBptr into the CPU registers.
	 *  Do the opposite operation of save context. 
	 *
	 * Input Parameters:
	 *  @param PCBptr: memory location of the PCB 
	 *  
	 * Output Parameters: 
	 *  None 
	 *  
	 * Method Return Values: 
	 *  None 
	 */
	public static void dispatcher(long PCBptr) {
		long[] PCBgpr = PCBs.get((int) PCBptr).getGPR().clone(); // copy whole GPR array into a temporary GPR array, then use temporary array to place values into CPU GPR array. Used clone() to create a clone array of PCB GPR array
		for(int i = 0; i < 8; i++) {
			gpr[i] = PCBgpr[i];
		}

		sp = PCBs.get((int) PCBptr).getSP();
		pc = PCBs.get((int) PCBptr).getPC();
		psr = UserMode; // user mode is 2, set system mode to user mode
	}



	/**
	 * Method Name: terminateProcess
	 * 
	 * Task Description: 
	 *  Method terminates a process by freeing the stack and PCB memory 
	 *  so that another process can have space. Return stack memory 
	 *  using stack start address and stack size in the given PCB.
	 *  Return PCB memory using the PCBptr. 
	 *  
	 * Input Parameters:
	 *  @param PCBptr: memory address location of PCB 
	 *  
	 * Output Parameters: 
	 *  None 
	 *  
	 * Method Return Values:
	 *  None 
	 */
	public static void terminateProcess(long PCBptr) {
		freeUserMemory(hypoMainMemory[(int) (PCBptr + PCBs.get((int) PCBptr).getStackStartAddress())],
				hypoMainMemory[(int) (PCBptr + PCBs.get((int) PCBptr).getStackSize())]);

		freeOSMemory(PCBptr, PCB_SIZE);
	}



	/**
	 * Method Name: allocateOSMemory
	 * 
	 * Task Description: 
	 *  Method allocates (takes aside) a block of OS free memory to be used.
	 *  
	 * Input Parameters: 
	 *  @param RequestedSize: memory size needed for allocation of block 
	 *  
	 * Output Parameters: 
	 *  None 
	 *  
	 * Function Return Values: 
	 *  @return ERROR_NO_FREE_MEMORY: if no free memory available 
	 *  @return ERROR_INVALID_MEMORY_SIZE: if requested memory size is invalid value
	 *  @return OK: success code, if memory allocation was completed successfully 
	 */
	public static long allocateOSMemory(long RequestedSize) {
		// ensure OS free memory exists
		if(OSFreeList == END_OF_LIST) {
			System.out.println("\nError: The OS free list is empty and there is no memory available to allocate. Returning error code: " + ERROR_NO_FREE_MEMORY);
			return ERROR_NO_FREE_MEMORY;
		}

		if(RequestedSize < 0) {
			System.out.println("\nError: The requested memory size is too small, it must be greater than 1. Returning error code: " + ERROR_INVALID_MEMORY_SIZE);
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

		System.out.println("\nError: No free OS memory. Returning error code: " + ERROR_NO_FREE_MEMORY);
		return ERROR_NO_FREE_MEMORY;
	}



	/**
	 * Method Name: freeOSMemory
	 * 
	 * Task Description: 
	 *  Method takes a memory location and memory size and tries to free 
	 *  in OS free list.
	 *
	 * Input Parameters:
	 *  @param ptr: Pointer of the block to free memory 
	 *  @param size: size of the block 
	 *  
	 * Output Parameters:
	 *  None 
	 *  
	 * Method Return Values:
	 *  @return ERROR_INVALID_MEMORY_ADDRESS: if invalid memory address given
	 *  @return ERROR_INVALID_SIZE_OR_MEMORY_ADDRESS: if invalid size or memory address given 
	 *  @return OK: success code, if memory was freed successfully 
	 */
	public static long freeOSMemory(long ptr, long size) {

	   if(ptr < START_ADDR_OF_OS_FREELIST || ptr > MAX_MEMORY_ADDRESS) {
	 		System.out.println("Error: Invalid memory address, memory address that you're trying to free is outside of OS free list area. Returning error code: " + ERROR_INVALID_MEMORY_ADDRESS);
	 		return ERROR_INVALID_MEMORY_ADDRESS;
	   }

		// check for minimum allocated size, which is 2 even if user asks for 1 location
		if(size == 1) {
			size = 2; // minimum allocated size
		}

		else if(size < 1 || (ptr + size) >= MAX_MEMORY_ADDRESS) {
			System.out.println("\nError: Invalid memory size. Returning error code: " + ERROR_INVALID_SIZE_OR_MEMORY_ADDRESS);
			return ERROR_INVALID_SIZE_OR_MEMORY_ADDRESS;
		}

		hypoMainMemory[(int) ptr] = OSFreeList; // make the given free block point to free block pointed by OS free list
		hypoMainMemory[(int) ptr + 1] = size; // set the free block size in the given free block
		OSFreeList = ptr; // set OS free list point to the given free block

		return OK;
	}



	/**
	 * Method Name: allocateUserMemory
	 * 
	 * Task Description: 
	 *  Method allocates a block of user memory to be used. 
	 *
	 * Input Parameters:
	 *  @param size: size needed for allocation of the block
	 *  
	 * Output Parameters:
	 *  None 
	 *  
	 * Method Return Values: 
	 *  @return ERROR_NO_FREE_MEMORY: if no free memory remaining 
	 *  @return ERROR_REQUESTED_MEMORY_TOO_SMALL: if size of requested memory is to small
	 *  @return OK: success code, if memory allocation was completed successfully
	 */
	public static long allocateUserMemory(long size) {
		if(UserFreeList == END_OF_LIST) {
			System.out.println("Error: The user free list is empty, no available memory to allocate. Returning error code: " + ERROR_NO_FREE_MEMORY);
			return ERROR_NO_FREE_MEMORY;
		}

		if(size < 0) {
			System.out.println("Error: The requested memory size is too small. Returning error code: " + ERROR_REQUESTED_MEMORY_TOO_SMALL);
			return ERROR_REQUESTED_MEMORY_TOO_SMALL;
		}

		if(size == 1) {
			size = 2;
		}

		long currentPtr = UserFreeList;
		long previousPtr = END_OF_LIST;

		while(currentPtr != END_OF_LIST) {
			if(hypoMainMemory[(int) (currentPtr + 1)] == size) {
				if(currentPtr == UserFreeList) {
					UserFreeList = hypoMainMemory[(int) currentPtr];
					hypoMainMemory[(int) currentPtr] = END_OF_LIST;
					return currentPtr;
				}

				else {
					hypoMainMemory[(int) previousPtr] = hypoMainMemory[(int) currentPtr];
					hypoMainMemory[(int) currentPtr] = END_OF_LIST;
					return currentPtr;
				}
			}

			else if(hypoMainMemory[(int) (currentPtr + 1)] > size) {
				if(currentPtr == UserFreeList) {
					hypoMainMemory[(int) (currentPtr + size)] = hypoMainMemory[(int) currentPtr];
					hypoMainMemory[(int) (currentPtr + size + 1)] = hypoMainMemory[(int) (currentPtr + 1)] - size;
					UserFreeList = currentPtr + size;
					hypoMainMemory[(int) currentPtr] = END_OF_LIST;
					return currentPtr;
				}
				else {
					hypoMainMemory[(int) (currentPtr + size)] = hypoMainMemory[(int) currentPtr];
					hypoMainMemory[(int) (currentPtr + size + 1)] = hypoMainMemory[(int) currentPtr + 1] - size;
					hypoMainMemory[(int) previousPtr] = currentPtr + size;
					hypoMainMemory[(int) currentPtr] = END_OF_LIST;
					return currentPtr;
				}
			}
			else {
				previousPtr = currentPtr;
				currentPtr = hypoMainMemory[(int) currentPtr];
			}
		}
		System.out.println("\nError: No free memory left. Returning error code: " + ERROR_NO_FREE_MEMORY);
		return ERROR_NO_FREE_MEMORY;
	}



	/**
	 * Method Name: freeUserMemory
	 * 
	 * Task Description: 
	 *  Free user memory at pointer memory location with amount of size as given by size
	 *
	 * Input Parameters:
	 *  @param ptr: block pointer to free memory for 
	 *  @param size: size of block to free user memory 
	 * 
	 * Output Parameters:
	 *  None 
	 *  
	 * Method Return Values: 
	 *  @return ERROR_INVALID_MEMORY_ADDRESS: if memory address trying to be freed is out of allowed range
	 *  @return ERROR_INVALID_SIZE_OR_MEMORY_ADDRESS: if memory address trying to be freed is out of allowed range or improper given size
	 *  @return OK: success code, if memory was successfully freed  
	 */
	public static long freeUserMemory(long ptr, long size) {

		// check that pointer is in given user free list range
	    if(ptr < START_ADDR_OF_USER_FREELIST || ptr > END_ADDR_OF_USER_FREELIST) {
	 		System.out.println("Error: Invalid memory address, memory address given is outside user free list range. Returning error code: " + ERROR_INVALID_MEMORY_ADDRESS);
	 		return ERROR_INVALID_MEMORY_ADDRESS;
   		}

		// check for minimum allocated size, which is 2 even if user asks for 1 location
		if(size == 1) {
			size = 2; // minimum allocated size
		}
		else if(size < 1 || (ptr + size) >= MAX_MEMORY_ADDRESS) {
			System.out.println("Error: Invalid memory address, memory address given is outside memory address range. Returning error code: " + ERROR_INVALID_SIZE_OR_MEMORY_ADDRESS);
			return ERROR_INVALID_SIZE_OR_MEMORY_ADDRESS;
		}

		hypoMainMemory[(int) ptr] = UserFreeList;
		hypoMainMemory[(int) ptr + 1] = size;
		UserFreeList = ptr;

		return OK;
	}



	/**
	 * Method Name: checkAndProcessInterrupt
	 *
	 * Task Description: 
	 *  Read interrupt ID number. Based on the interrupt ID,
	 *  service the interrupt by calling appropriate method using switch
	 * 
	 * Input Parameters: 
	 *  None
	 *  
	 * Output Parameters: 
	 *  None 
	 *  
	 * Method Return Values:
	 *  @return interruptID: return entered interrupt ID   
	 *  @throws IOException
	 */
	public static long checkAndProcessInterrupt() throws IOException {

		// prompt possible interrupts selection menu
		System.out.println("\n***********************************************"
							+ "\n\tPossible Interrupts: \n\t0 - no interrupt"
							+ "\n\t1 - run program\n\t2 - shutdown system\n\t"
							+ "3 - input operation completion (io_getc)\n\t"
							+ "4 - output operation completion (io_putc)\n"
							+ "\n***********************************************");

		System.out.print("Please choose an interrupt number: ");
		// read interrupt ID
		int interruptID = scan.nextInt();
		System.out.println("Interrupt ID entered: " + interruptID);

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

			default: System.out.println("\nError: Invalid interrupt ID entered. Error code: " + ERROR_INVALID_ID); // invalid interrupt ID
					 break;
		}
		return interruptID;
	}



	/**
	 * Method Name: isrRunProgramInterrupt
	 *
	 * Task Description: 
	 *  Run program interrupt service routine (ISR).
	 *  Read filename and create process
	 *  
	 * Input Parameters: 
	 *  None 
	 *  
	 * Output Parameters: 
	 *  None 
	 *  
	 * Method Return Values:
	 *  None 
	 *  @throws IOException
	 */
	public static void isrRunProgramInterrupt() throws IOException {

		// prompt and read filename
		System.out.print("Enter name of program to run - machine language program (add .txt to the end): ");
		String filename = scan.next();

		// create the process
		createProcess(filename, DEFAULT_PRIORITY);
	}



	/**
	 * Method Name: isrInputCompletionInterrupt
	 * 
	 * Task Description: 
	 *  Method services a 'input completion' request.
	 *  By reading a PID entered by user, method searches the WQ for the PCB that 
	 *  is a matched. This is input character is stored in GPR1. 
	 *  
	 * Input Parameters: 
	 *  None 
	 *  
	 * Output Parameters: 
	 *  None 
	 *  
	 * Method Return Values: 
	 *  None 
	 */
	public static void isrInputCompletionInterrupt() {
		System.out.print("\nEnter PID of the process completing input completion interrupt: ");
		int PID = scan.nextInt();

		long PCBptr = searchAndRemovePCBFromWQ(PID); // search WQ to find the PCB having the given PID

		if(PCBptr > 0) {
			System.out.println("Enter a character to store: ");
			char inputCharacter = scan.next().charAt(0);
			// store inputCharacter in the GPR[1] in the PCB
			long[] GPRpcb = new long[1]; // temporary array created
			GPRpcb[1] = inputCharacter; // type cast char to long
			PCBs.get((int) ThisPCB).setGPR(GPRpcb, 1);

			PCBs.get((int) ThisPCB).setState(READY_STATE);
			insertIntoRQ(PCBptr);
		}
	}



	/**
	 * Method Name: isrOutputCompletionInterrupt
	 * 
	 * Task Description: 
	 *  Method serves a 'input completion' request given by user. 
	 *  It reads a PID entered by user, searches the WQ for the PCB that matches,
	 *  and finally takes the input character and stores it in GPR1. 
	 *  
	 * Input Parameters: 
	 *  None 
	 *  
	 * Output Parameters: 
	 *  None 
	 *  
	 * Method Return Values: 
	 *  None 
	 */
	public static void isrOutputCompletionInterrupt() {
		System.out.print("\nEnter PID of the process completing input completion interrupt: ");
		int PID = scan.nextInt();

		long PCBptr = searchAndRemovePCBFromWQ(PID);

		if(PCBptr > 0) {
			// get whole GPR array and store in temporary array
			long[] PCBgpr = PCBs.get((int) ThisPCB).getGPR();
			// then retrieve index 1 of GPR array and assign to variable
			char outputCharacter = (char) PCBgpr[1];

			System.out.println("Character in the GPR in PCB: " + outputCharacter);
			PCBs.get((int) ThisPCB).setState(READY_STATE); // set process state to ready in PCB
			insertIntoRQ(PCBptr);
		}
	}



	/**
	 * Method Name: isrShutdownSystem
	 *
	 * Task Description: 
	 *  terminate all processes in RQ and WQ and exit from the program.
	 *  This is the only place that the operating system program should exit.
	 *  
	 * Input Parameters: 
	 *  None 
	 *  
	 * Output Parameters: 
	 *  None 
	 *  
	 * Method Return Values: 
	 *  None 
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
	 * Method Name: searchAndRemovePCBFromWQ
	 *
	 * Task Description: 
	 *  Search the WQ for the matching PID.
	 *  When a match is found remove it from WQ and return PCB pointer.
	 *  If no match is found, return invalid PID error code.
	 *
	 * Input Parameters: 
	 *  @param PID: process ID of which PCB will be removed from WQ
	 *  
	 * Output Parameters:
	 *  None 
	 *  
	 * Method Return Values: 
	 *  @return END_OF_LIST: returns value in END_OF_LIST 
	 *  @return currentPCBptr: returns current PCBptr 
	 */
	public static long searchAndRemovePCBFromWQ(long PID) {
		long currentPCBptr = WQ;
		long previousPCBptr = END_OF_LIST;

		// search WQ for a PCB that has the given PID, if a match is found, remove it from WQ and return the PCB pointer
		while(currentPCBptr != END_OF_LIST) {
			if(hypoMainMemory[(int) ((PCBs.get((int) ThisPCB).getPCBptr()) + PCBs.get((int) ThisPCB).getPID())] == PID) {

				if(previousPCBptr == END_OF_LIST) { // match found, remove from WQ
					// first PCB
					WQ = hypoMainMemory[(int) (currentPCBptr + PCBs.get((int) ThisPCB).getNextPCBPointer())];
				}

				else {
					// not first PCB
					hypoMainMemory[(int) (previousPCBptr +  PCBs.get((int) ThisPCB).getNextPCBPointer())] =
							hypoMainMemory[(int) (previousPCBptr + PCBs.get((int) ThisPCB).getNextPCBPointer())];
				}

				hypoMainMemory[(int) (currentPCBptr + PCBs.get((int) ThisPCB).getNextPCBPointer())] = END_OF_LIST;
				return currentPCBptr;
			}
			previousPCBptr = currentPCBptr;
			currentPCBptr = hypoMainMemory[(int) (currentPCBptr + PCBs.get((int) ThisPCB).getNextPCBPointer())];
		}

		// No matching PCB is found, display PID message and return end of list code
		System.out.println("PID not found");
		return END_OF_LIST;
	}



	/**
	 * Method Name: systemCall
	 * 
	 * Task Description: 
	 *  Method takes a system call ID as argument and runs command that is 
	 *  associated to this specific system call ID. 
	 *  
	 * Input Parameters: 
	 *  @param systemCallID: system call ID number 
	 * 
	 * Output Parameters: 
	 *  None 
	 *  
	 * Method Return Values: 
	 *  @return status: returns status of method 
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

		// message send
		case 6: System.out.println("Message send system call not implemented");
				break;

		// message receive
		case 7: System.out.println("Message receive system call not implemented");
				break;

		// IO_getC - input a single character
		case 8: status = io_getcSystemCall();
				break;

		// IO_getC - output a single character
		case 9: status = io_getcSystemCall();
				break;

		// invalid system call ID
		default: System.out.println("Invalid system call ID error");
				 break;
		}

		psr = UserMode;

		return status;
	}



	/**
	 * Method Name: memAllocSystemCall
	 * 
	 * Task Description: 
	 *  Method takes a system call.
	 *  
	 * Input Parameters: None 
	 * 
	 * Output Parameters: 
	 *  GPR0: return value code 
	 *  GPR1: return value code 
	 *  
	 * Method Return Values: 
	 *  @return ERROR_INVALID_MEMORY_SIZE: if invalid memory size is found 
	 *  @return GPR[0]: returns value of CPU GPR index 0 
	 */
	public static long memAllocSystemCall() {
		long size = gpr[2];

		// check for size out of range
		if(size < 1) {
			System.out.println("The size of requested memory to be freed is out of range");
			return ERROR_INVALID_MEMORY_SIZE;
		}

		// check size of 1 and change it to 2
		if(size == 1) {
			size = 2;
		}

		gpr[1] = allocateUserMemory(size);

		if(gpr[1] < 0) {
			gpr[0] = gpr[1]; // set gpr0 to have the return status
		}
		else {
			gpr[0] = OK;
		}

		System.out.println(memAllocSystemCall() + "\n" + gpr[0] + gpr[1] + gpr[2] + "\n");
		return gpr[0];
	}



	/**
	 * Method Name: memFreeSystemCall
	 * 
	 * Task Description: 
	 *  Method takes a system call. 
	 *  
	 * Input Parameters: 
	 *  None
	 *
	 * Output Parameters: 
	 *  None 
	 * 
	 * Method Return Values: 
	 *  @return GPR[0]: return value code of GPR[0]
	 */
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



	/**
	 * Method Name: io_getcSystemCall
	 * 
	 * Task Description: 
	 *  Method takes a system call.
	 *  
	 * Input Parameters: 
	 *  None 
	 *  
	 * Output Parameters: 
	 *  None 
	 *
	 * Method Return Values: 
	 *  @return IO_GETCINTERRUPT: returns value in variable 
	 */
	public static long io_getcSystemCall() {
		System.out.println("\nInput operation required, leaving CPU for input interrupt\n");
		return IO_GETCINTERRUPT;
	}



	/**
	 * Method Name: io_putcSystemCall
	 * 
	 * Task Description: 
	 *  Method takes a system call. 
	 *
	 * Input Parameters:
	 *  None 
	 *  
	 * Output Parameters: 
	 *  None 
	 *
	 * method Return values;
	 *  @return IO_PUTCINTERRUPT: returns value in variable 
	 */
	public static long io_putcSystemCall() {
		System.out.println("\nOutput operation required, leaving CPU for output interrupt\n");
		return IO_PUTCINTERRUPT;
	}
}




/**
 * Class Name: PCB
 *
 * Description: Class to contain all information on PCB.
 * Class represents a process' program control block (PCB).
 * PCB is located in HYPO machine's OS dynamic memory area.
 *
 * */
class PCB {

	public long PCBptr; // PCB start address
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
		this.GPR[(int) GPRIndex] = GPR[(int) GPRIndex]; // set individual array index address, passing in array (containing values) and index address
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