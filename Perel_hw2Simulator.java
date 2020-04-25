import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;

/**
 * @authors Brian Perel, Jon Petani 
 * @version 1.0
 *
 * HW# 2
 * Date 02/05/20
 *
 * HYPO Project for OSI course CSCI 465
 *
 * Purpose:
 *	This program will simulate a hypothetical decimal (2 address) machine
 *  which runs a real-time multitasking operating system (MTOPS),
 *	designed for microcomputers. We will be using the hardware of
 *  the host machine to run the simulated OS. We are building a decimal machine rather than binary.
 *  All hardware is simulated. If the system reaches a halt that indicates successful execution of a process 
 *  Features covered in this project include:
 *  	-scheduling algorithms, memory management,
 *  	-process synchronization, interprocess communication, I/O management, timer management,
 *  	-simulation of a 2-address machine with a stack, event simulation,
 *  	-assembly language programming and hand assembly to machine language program,
 *  	-and an absolute loader.
 *  
 *  MTOPS is an interrupt (event) driven time sharing multitasking (multiprocessing) operating system. 
 *
 *  User programs to test the hardware and operating system are written in assembly language first and then hand assembled
 *  into machine language using a symbol table that contains 2 columns for every row: column1 = name of label appearing 
 *  in the label of field of assembly language instruction, column2 = address of instruction that has label.
 *  
 *  This machine could be simulated on any computer 
 *  
 *  Computer memory and CPU registers = Memory Address Register (MAR) -> contains address of the memory location to be accessed, 
 *  Memory Buffer Register (MBR) -> contains the value of the location to be read or written , 
 *	Random Access Memory (RAM) -> Main Memory, 
 *  Instruction Register (IR) -> The instruction decoder is attached to the IR register to identify (decode) the instruction 
 *  	that has been fetched during fetch cycle of an instruction execution, 
 *  Stack Pointer (SP) -> contains content of memory address in the stack, it points to the top of the stack,
 *  Program Counter (PC) -> contains address of the instruction memory location (address) to be fetched,
 *  General Purpose Register (GPR),
 *  Processor Status Register (PSR),
 *  Arithmetic Logic Unit (ALU),
 *  Clock -> System clock in microseconds 
 *  
 *  We will first write a HYPO machine simulation, then use the HYPO's hardware to write and run the MTOPS machine on it. 
 *  Essentially MTOPS will manage HYPO's hardware. Without the hardware there is no task for the OS and hence there is no need for the OS.
 *  We will simulate the hardware components by software rather than by building the hardware.
 *  
 *  The OS will implement 1. memory allocation and free system calls, 2.character-oriented input and output system calls, 
 *  3. processes creation and termination, 4. stack operations (push and pop), 5. create a null system process to run when there
 *  is no other process in the ready queue, 6. process scheduling using priority round robin algorithm, 7. maintain user mode and OS mode in
 *  the PSR, 8. handle user commands and resulting interrupt handling, 9. handling of system interrupts, 10. display of context in ready queue,
 *  waiting queue, and PCB.   
 *  
 */
public class Perel_hw2Simulator {

	/* HYPO Machine hardware components global variables (here we are simulating the hardware components by software) */
	static long hypoMainMemory[] = new long[10000]; // simulation of main memory array (RAM), size of memory is 10000 words
	static long mar, mbr, clock, IR, psr, pc, sp; // simulation of memory addresses
	static long gpr[] = new long[8]; // simulation of general purpose registers, 0 to 7 (size 8)
	
	/* PCB is an array located in HYPO machine's OS dynamic main memory area. 
	PCB is a data structure used by OS to store all information about a process, 
	information about process is updated during transition of process state (waiting to ready to running process states).
	The OS returns a pointer to the dynamically allocated PCB memory when a request is made. The PCB memory is released 
	when the process is terminated from the system. 
	PCB is allocated from OS at process creation time. PCB size is 22, locations 0-21. */ 
	
	// below are PCB variables that hold there associated array index values 
	static int PCBptrIndex = 0; // PCB start address
	static int nextPCBptrIndex = 1;
	static int PIDIndex = 2;
	static int stateIndex = 3;
	static int reasonForWaitingCodeIndex = 4;
	static int priorityIndex = 5;
	static int stackStartAddressIndex = 6;
	static int stackSizeIndex = 7;
	static int messageQueueStartAddressIndex = 8;
	static int messageQueueSizeIndex = 9;
	static int numOfMessagesInQueueIndex = 10;
	static int GPR0Index = 11;
	static int GPR1Index = 12;
	static int GPR2Index = 13;
	static int GPR3Index = 14;
	static int GPR4Index = 15;
	static int GPR5Index = 16;
	static int GPR6Index = 17;
	static int GPR7Index = 18;
	static int SPIndex = 19;
	static int PCIndex = 20;
	static int PSRIndex = 21;

	final static long PROGRAM_HALTED = 1; // halt instruction code
	final static long OK = 0; // status code for successful execution
	final static long END_OF_PROGRAM = -1; // variable to indicate that end of machine program has been reached

	final static long END_OF_LIST = -1; // variable to indicate that end of OS or User Free List has been encountered
	static long RQ = END_OF_LIST; // ready queue is set to end of list, ready queue is the list where processes that are ready to run are held 
	static long WQ = END_OF_LIST; // waiting queue is set to end of list, waiting queue is the list where processes that are waiting to be run are held 
	static long OSFreeList = END_OF_LIST; // set User Free List to empty list
	static long UserFreeList = END_OF_LIST; // set User Free List to empty list
	static long ProcessID = 1; // variable to hold the current process ID, incremented by 1 every time a new process is created. PID is a unique ID assigned to every process when it is created
	static long stackSize = 10; // size of the memory stack 
	static long OSMode = 1; // variable to set system mode to OS Mode, Mode 1 
	static long UserMode = 2; // variable to set system mode to User Mode, Mode 2 
	static boolean shutdown = false; // flag used to indicate the HYPO Machine should shutdown
	final static long DEFAULT_PRIORITY = 128; // set default priority to middle value in priority range
	
	/* As a process executes its program (instructions) will go through several states, a process in MTOPS can be 
	1 of 3 states: ready state, running state, waiting state. When a process is selected by the OS to give CPU, 
	it is given a fixed amount of time called time slice. Time slice is set to 200 milliseconds (ticks) */
	
	final static long READY_STATE = 1; // variable to indicate process ready state. State transition 1 of process scheduling. 
	final static long WAITING_STATE = 2; // variable to indicate process waiting state. State transition 2 of process scheduling. 
	final static long RUNNING_STATE = 3; // variable to indicate process running state. State transition 3 of process scheduling. 
	final static long TIMESLICE = 200; // variable time slice is set to 200 clock ticks, a time slice is the amount of time a process is allowed to run uninterrupted (CPU is only given a process for a fixed time)
	final static long MAX_MEMORY_ADDRESS = 9999; // the highest user memory address you can use, 9999 because the memory array size is 10,000 
	final static long HALT_IN_PROGRAM_REACHED = 1; // variable used to indicate the CPU() has reached a halt
	final static long TIME_SLICE_EXPIRED = 2; // variable used to indicate that a time slice expiration point was reached
	final static long START_ADDR_OF_OS_FREELIST = 3500; // variable to mark starting address of OS free list
	final static long END_ADDR_OF_OS_FREELIST = 6998; // variable to hold end of OS free list address 
	final static long START_ADDR_OF_USER_PROGRAM_AREA = 0; // variable to hold starting address of the user program area
	final static long END_ADDR_OF_USER_PROGRAM_AREA = 3499; // variable to hold end address of user program area
	final static long START_ADDR_OF_USER_FREELIST = 2500; // variable to mark start address of user free list
	final static long END_ADDR_OF_USER_FREELIST = 4499; // variable to mark end address of user free list
	final static long IO_GETCINTERRUPT = 3; // variable used when "input operation is completed" interrupt is encountered
	final static long IO_PUTCINTERRUPT = 4; // variable used when "output operation is completed" interrupt is encountered
	final static long PCB_SIZE = 22; // variable holds value which is number of indexes in PCB array (size) 
	
	/* HYPO machine error codes, error codes are less than 0, check for errors at every step of OS execution */
	final static long RUN_TIME_ERROR = -2; // error code to indicate run time error was encountered 
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
	final static long ERROR_FILE_NOT_FOUND = -18; // error code that file was not found error encountered 

	static Scanner scan = new Scanner(System.in); // console input object instance


	/**
	 * Brian Perel wrote this method 
	 * 
	 * Method Name: main
	 * 
	 * Method Description: 
	 *   Initialize all hardware parts, OS free list and user free list. Start the OS, begin a null process.  
	 *   Enter OS environment by going to checkAndProcessInterrupt menu and servicing interrupts.  
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

		initializeSystem(); // initialize all OS hardware, reset memory when OS starts

		System.out.println("\nStarting OS...");

		// main loop of HYPO machine runs until shutdown, enter OS environment 
		while(!shutdown) {
			long status = checkAndProcessInterrupt(); // check and process interrupt			

			if(status == 2) break; // if interrupt is shutdown, terminate program

			System.out.println("\nRQ: Before CPU scheduling"); // dump the contents of RQ
			printQueue(RQ);

			System.out.println("\nWQ: Before CPU scheduling"); // dump the contents of WQ
			printQueue(WQ);

			dumpMemory("Dynamic memory area before CPU scheduling", 0, 99); // print context of memory 

			// select next process from RQ to give CPU
			long PCBrunningptr = selectProcessFromRQ(); // select a process from ready queue

			// perform restore context using dispatcher
			dispatcher(PCBrunningptr);
			
			System.out.println("\nRQ: After selecting process from RQ"); // dump the contents of RQ
			printQueue(RQ); // print context of queue containing all processes 

			System.out.println("\nDumping the PCB contents of the running PCB"); // dump the contents of WQ
			printPCB(PCBrunningptr);
		
			// execute instructions of the running process using the CPU
			System.out.println("\n\nExecuting CPU...");
			status = CPU();
			System.out.println("\nCPU execution completed");

			dumpMemory("\nDynamic memory area after executing program", 0, 99);

			if(status == TIME_SLICE_EXPIRED) {
				System.out.println("\nTime slice has expired, saving context and inserting back into RQ");
				saveContext(PCBrunningptr); // Save CPU Context of running process in its PCB, because the running process is losing control of the CPU.
				insertIntoRQ(PCBrunningptr); // Insert running process PCB into RQ.
				PCBrunningptr = END_OF_LIST; // Set the running PCB pointer to the end of list.
			}
			else if(status == HALT_IN_PROGRAM_REACHED || status < 0) {
				System.out.println("\nHalt in program reached, end of program");
				terminateProcess(PCBrunningptr);
				PCBrunningptr = END_OF_LIST;
			}
			else if(status == io_getcSystemCall()) {
				System.out.println("\nInput Interrupt detected");
				hypoMainMemory[(int) (PCBrunningptr + reasonForWaitingCodeIndex)] = io_putcSystemCall(); //Set reason for waiting in the running PCB to Output Completion Event
				insertIntoWQ(PCBrunningptr); // insert running process into WQ.
				PCBrunningptr = END_OF_LIST; // set running PCB pointer to end of list
			}
			else if(status == io_putcSystemCall()) {
				System.out.println("\nOutput interrupt detected");
				hypoMainMemory[(int) (PCBrunningptr + reasonForWaitingCodeIndex)] = io_putcSystemCall(); //Set reason for waiting in the running PCB to Output Completion Event
				insertIntoWQ(PCBrunningptr); // insert running process into WQ.
				PCBrunningptr = END_OF_LIST; // set running PCB pointer to end of list
			}
			else {
				System.out.println("Unkown programming error detected");
			}
		}

		System.out.println("OS is shutting down...\nReturning code: " + OK + "\nGoodbye");
	}



	/**
	 * Brian Perel wrote this method 
	 * 
	 * Method Name: initializeSystem
	 *
	 * Method Description:
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
		hypoMainMemory[(int) (UserFreeList + nextPCBptrIndex)] = END_OF_LIST;
		hypoMainMemory[(int) (UserFreeList + 1)] = START_ADDR_OF_USER_FREELIST;

		// create OS free list using the free block address and size
		OSFreeList = START_ADDR_OF_OS_FREELIST;
		hypoMainMemory[(int) (OSFreeList + nextPCBptrIndex)] = END_OF_LIST; 
		hypoMainMemory[(int) (OSFreeList + 1)] = START_ADDR_OF_OS_FREELIST; 

		System.out.print("Hardware units successfully initialized!");

		// create a null process with lowest priority (0) to run when there is no other process in the ready queue 
		String filename = "Perel-hw2MachineProgram1.txt";
		createProcess(filename, 0);
	}



	/**
	 * Brian Perel wrote this method 
	 * 
	 * Method Name: absoluteLoader
	 *
	 * Method Description:
	 *   Open the file containing HYPO machine user program
	 *   and load the content into HYPO machine memory,
	 *   If successful load, return the PC value in end of
	 *   program line. Ensure the program file is of proper
	 *   format and that address/PC values are within the
	 *   User Program area. If failure, display appropriate
	 *   error message and return error code.
	 *
	 * Input Parameters:
	 *   @param String filename: String specifying the name of file of machine language program to open and load. File must exist in project working directory
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
	public static long absoluteLoader(String file) throws IOException, FileNotFoundException {
		
			// load the program from given filename into HYPO main memory
			try {
				File fileObj = new File(file); // create file object
				BufferedReader br = new BufferedReader(new FileReader(fileObj)); // create bufferedReader object to read from file
				
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

				// access table in file, each line is 1 record, use a delimiter (space) to assign first number in left column of record to address variable and then assign second number to second variable
				// read 1 line of file at a time until null is detected (end of file)
				while((st = br.readLine()) != null)  {
					temp = st.split("\t", 2)[0]; // read from file and split string by the tab, whatever is before goes into variable (read first column of machine code file line)
					temp2 = st.split("\t", 2)[1]; // read from file and split string b the tab, but now whatever is after goes into variable (read second column of machine code file line)
					address = Long.parseLong(temp.trim()); // use trim() to eliminate extra whitespace between address and content values in line from file
					content = Long.parseLong(temp2.trim()); // use trim() to eliminate extra whitespace between address and content values in line from file

					// -1 is not a register address, it is an indicator for end of program. Successful program execution should come to here
					if(address == END_OF_PROGRAM) {
						System.out.print("\nProgram Successfully Loaded!");
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
				System.out.println("\nError: coudn't open the file. Returning error code: " + ERROR_FILE_OPEN);
				return ERROR_FILE_OPEN;
			}

			// return error code if program reaches this point since end of program could not be detected
			return ERROR_NO_END_OF_PROGRAM;
	}



	/**
	 * Brian Perel wrote this method 
	 * 
	 * Method Name: CPU
	 *
	 * Method Description:
	 *   Method (executes the loaded program in main memory) performs fetch-decode-execute cycle for
	 *   every given instruction. Simulates the CPU hardware of the OS.
	 *   Performs all possible error checking such as invalid memory address
	 *   reference, invalid mode, division by zero. After execution of
	 *   every instruction, it increases the clock by the instruction execution
	 *   time. Method executes 1 instruction at a time pointed by program counter.
	 *   The process when it gets the CPU is given a fixed amount of CPU called time slice. 
	 *   For every instruction executed, decrement the CPU time left by the instruction time.
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
					System.out.println("Value " + Op1Value + " is being pushed to the stack");
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
					System.out.println("Value " + Op1Value + " is being popped from the stack");
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

				case 12: { // system call instruction - system call requests OS services such as reading from keyboard, displaying to monitor, create/delete/suspend process, send message. The type of message depends on the system call identifier specified in the instruction
					// check if PC value is in invalid range
					if(pc <= 0 && pc >= 2499) {
						System.out.println("Error invalid PC value encountered. Returning error code: " + ERROR_INVALID_PC_VALUE);
						return ERROR_INVALID_PC_VALUE;
					}
					
					recieve = fetchOperand(Op1Mode, Op1GPR);
					Op1Value = recieve.getOpValue();
					Op1Address = recieve.getOpAddress();
					status = recieve.getStat();

					if(!(status > 0))  {
						System.out.println(status);
						return ERROR_INVALID_ADDRESS;
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
	 * Brian Perel wrote this method 
	 * 
	 * Method Name: fetchOperand
	 *
	 * Method Description: take in operand mode to determine which case is to be executed
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
	 * Brian Perel wrote this method 
	 * 
	 * Method Name: dumpMemory
	 *
	 * Method Description:
	 *  Displays the content of the HYPO machine GPRs, the clock,
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
	 * Brian Perel wrote this method 
	 * 
	 * Method Name: create process
	 *
	 * Method Description: Method creates the process for the program, 
	 *  prepares the PCB block (object), initializes all PCB contents to 
	 *  proper values, defines stack space for the program and dumps user 
	 *  program area memory locations, loads program (process) from disk to memory.
	 *  Every process created is assigned a stack area in RAM and a pointer in 
	 *  SP by the OS. Stack size is predefined by OS. Programs have access to SP 
	 *  through push and pop instructions only.    
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

		// Allocate stack space (memory) for PCB to create a process
		long PCBptr = allocateOSMemory(PCB_SIZE); // change argument later, 0 gives error, 1 works

		// check return value from allocateOSMemory(), if < 0 then error encountered
		if(PCBptr < 0) {
			return PCBptr;
		}

		initializePCB(PCBptr); // call initialize PCB pointer and set it to a variable (so we know which PCB we're working with)

		// load the program from disk to memory 
		long value = absoluteLoader(filename);

		if(value < 0) {
			return value; // check for program loading error
		}

		hypoMainMemory[(int) (PCBptr + PCIndex)] = value;
		
		// Allocate stack space from user free list, allocate user memory of size stack size
		long ptr = allocateUserMemory(stackSize);

		// check for error
		if(ptr < 0) {
			// User memory allocation failed
			freeOSMemory(PCBptr, value);
			return ptr;
		}

		hypoMainMemory[(int) (PCBptr + SPIndex)] = ptr + stackSize;
		hypoMainMemory[(int) (PCBptr + stackStartAddressIndex)] = ptr;
		hypoMainMemory[(int) (PCBptr + stackSizeIndex)] = stackSize;
		
		// set priority in the PCB to priority
		hypoMainMemory[(int) (PCBptr + priorityIndex)] = priority; 

		dumpMemory("\nDumping memory addresses in user program area", 0, 99);

		printPCB(PCBptr); // print PCB passing PCBptr
		
		insertIntoRQ(PCBptr); // insert PCB into ready queue passing PCBptr

		return OK;
	}



	/**
	 * Brian Perel wrote this method 
	 * 
	 * Method Name: initializePCB
	 *
	 * Method Description: PCB (Process Control Block) is related to process - anything that calls create process will deal with PCB such as initializeSystem function
	 *  Method is used to initialize a new PCB node in which we set all its element variables. The new PCB will start at the specified address and have the specified PID.
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
	public static void initializePCB(long PCBptr) {

		// initialize all PCB values to 0 
		for(int i = 0; i < PCB_SIZE; i++) {
			hypoMainMemory[(int) (PCBptr + i)] = 0;
		}

		// PID of value zero is invalid, since process id's value is going into PID method we check process id value
		if(ProcessID == 0) {
			System.out.println("Invalid PID given. Error code: " + ERROR_INVALID_ADDRESS);
		}
		
		hypoMainMemory[(int) (PCBptr + PIDIndex)] = ProcessID++; // allocate PID and set it in the PCB
		hypoMainMemory[(int) (PCBptr + priorityIndex)] = DEFAULT_PRIORITY; // set priority field in the PCB to default priority
		hypoMainMemory[(int) (PCBptr + stateIndex)] = READY_STATE; // set state field in the PCB equal to ready state
		hypoMainMemory[(int) (PCBptr + nextPCBptrIndex)] = END_OF_LIST; // set next PCB pointer field (next pointer in the list)  in the PCB to end of list
	}



	/**
	 * Jon Petani wrote this method 
	 * 
	 * Method Name: printPCB
	 *
	 * Method Description: 
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
	
		System.out.println("\nContents of the PCB in memory address: " + PCBptr +
				"\nPCB address = " + PCBptr +
				", Next PCB ptr = " + hypoMainMemory[(int) (PCBptr + nextPCBptrIndex)] +
				", PID = " + hypoMainMemory[(int) (PCBptr + PIDIndex)] +
				", Reason for waiting code = " + hypoMainMemory[(int) (PCBptr + reasonForWaitingCodeIndex)] + 
				", State = " + hypoMainMemory[(int) (PCBptr + stateIndex)] +
				",\nMessage queue start address = " + hypoMainMemory[(int) (PCBptr + messageQueueStartAddressIndex)] +
				", Message queue size = " + hypoMainMemory[(int) (PCBptr + messageQueueSizeIndex)] +
				", Number of messages in queue = " + hypoMainMemory[(int) (PCBptr + numOfMessagesInQueueIndex)] +  
				",\nPSR = " + hypoMainMemory[(int) (PCBptr + PSRIndex)] + 
				", PC = " + hypoMainMemory[(int) (PCBptr + PCIndex)] +
				", SP = " + hypoMainMemory[(int) (PCBptr + SPIndex)] +
				", Priority = " + hypoMainMemory[(int) (PCBptr + priorityIndex)] +
				", Stack info: start address = " + hypoMainMemory[(int) (PCBptr + stackStartAddressIndex)] +
				" , size = " + hypoMainMemory[(int) (PCBptr + stackSizeIndex)]);

				// print 8 GPR values: GPRs = print 8 values of GPR 0 to GPR 7
				System.out.print("GPRs:\t");
				System.out.print("GPR0" + ": " + hypoMainMemory[(int) (PCBptr + GPR0Index)] + " ");
				System.out.print("GPR1" + ": " + hypoMainMemory[(int) (PCBptr + GPR1Index)] + " ");
				System.out.print("GPR2" + ": " + hypoMainMemory[(int) (PCBptr + GPR2Index)] + " ");
				System.out.print("GPR3" + ": " + hypoMainMemory[(int) (PCBptr + GPR3Index)] + " ");
				System.out.print("GPR4" + ": " + hypoMainMemory[(int) (PCBptr + GPR4Index)] + " ");
				System.out.print("GPR5" + ": " + hypoMainMemory[(int) (PCBptr + GPR5Index)] + " ");
				System.out.print("GPR6" + ": " + hypoMainMemory[(int) (PCBptr + GPR6Index)] + " ");
				System.out.print("GPR7" + ": " + hypoMainMemory[(int) (PCBptr + GPR7Index)] + " ");

				System.out.println();
	}



	/**
	 * Jon Petani wrote this method 
	 * 
	 * Method Name: printQueue
	 * 
	 * Method Description: 
	 *  Walk through the queue from the given pointer until the end of list
	 *  and print the given queue passed in as argument, queue can be
	 *  ready queue or waiting queue. 
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
			currentPCBptr = hypoMainMemory[(int) (currentPCBptr + nextPCBptrIndex)];
		}

		return OK;
	}



	/**
	 * Jon Petani wrote this method 
	 * 
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

		hypoMainMemory[(int) (PCBptr + stateIndex)] = READY_STATE; 
		hypoMainMemory[(int) (PCBptr + nextPCBptrIndex)] = END_OF_LIST; 

		// if RQ is empty
		if(RQ == END_OF_LIST) {
			RQ = PCBptr;
			return OK;
		}

		// Walk through RQ and find the place to insert. PCB will be inserted at the end of its priority
		while(currentPtr != END_OF_LIST) {
			if(hypoMainMemory[(int) (PCBptr + priorityIndex)] > hypoMainMemory[(int) currentPtr + priorityIndex]) {
				// found the place to insert
				if(previousPtr == END_OF_LIST) {
					// enter PCB in the front of the list as first entry
					hypoMainMemory[(int) (PCBptr + nextPCBptrIndex)] = RQ;
					RQ = PCBptr;
					return OK;
				}

				// enter PCB in the middle of the list
				hypoMainMemory[(int) (PCBptr + nextPCBptrIndex)] = hypoMainMemory[(int) (previousPtr + nextPCBptrIndex)];
				hypoMainMemory[(int) (previousPtr + nextPCBptrIndex)] = PCBptr;
				return OK;
			}
			else {  // PCB to be inserted has lower or equal priority to the current PCB in RQ
				// go the the next PCB in RQ
				previousPtr = currentPtr;
				currentPtr = hypoMainMemory[(int) (currentPtr + nextPCBptrIndex)];
			}
		}

		// insert PCB at the end of the RQ
		hypoMainMemory[(int) (previousPtr + nextPCBptrIndex)] = PCBptr;
		return OK;
	}



	/**
	 * Jon Petani wrote this method 
	 * 
	 * Method Name: insertIntoWQ
	 * 
	 * Method Description: 
	 *  Take PCBptr and insert it into the front of waiting queue after 
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

		hypoMainMemory[(int) (PCBptr + stateIndex)] = WAITING_STATE; // set state to ready state
		hypoMainMemory[(int) (PCBptr + nextPCBptrIndex)] = WQ; // set next pointer to end of list
		WQ = PCBptr;

		return OK;
	}



	/**
	 * Jon Petani wrote this method 
	 * 
	 * Method Name: selectProcessFromRQ
	 *
	 * Method Description: 
	 *  Select first process from RQ to give CPU. When CPU has to be allocated to the next process in RQ, select the first process
	 *  in the RQ and return the pointer to the PCB since processes in RQ are already ordered from highest to lowest priority.
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

		if(RQ != END_OF_LIST) {
			RQ = hypoMainMemory[(int) (RQ + nextPCBptrIndex)]; // remove first PCB from RQ, set RQ to next PCB pointed by RQ
		}

		hypoMainMemory[(int) (PCBptr + nextPCBptrIndex)] = END_OF_LIST; // set next point to END_OF_LIST in the PCB; set next PCB field in the given PCB to END_OF_LIST
		return PCBptr;
	}



	/**
	 * Brian Perel wrote this method 
	 * 
	 * Method Name: saveContext
	 *
	 * Method Description:
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
		hypoMainMemory[(int) (PCBptr + GPR0Index)] = gpr[0];
		hypoMainMemory[(int) (PCBptr + GPR1Index)] = gpr[1];
		hypoMainMemory[(int) (PCBptr + GPR2Index)] = gpr[2];
		hypoMainMemory[(int) (PCBptr + GPR3Index)] = gpr[3];
		hypoMainMemory[(int) (PCBptr + GPR4Index)] = gpr[4];
		hypoMainMemory[(int) (PCBptr + GPR5Index)] = gpr[5];
		hypoMainMemory[(int) (PCBptr + GPR6Index)] = gpr[6];
		hypoMainMemory[(int) (PCBptr + GPR7Index)] = gpr[7];
		
		hypoMainMemory[(int) (PCBptr + SPIndex)] = sp;
		hypoMainMemory[(int) (PCBptr + PCIndex)] = pc;
		hypoMainMemory[(int) (PCBptr + PSRIndex)] = psr;
	}



	/**
	 * Jon Petani wrote this method 
	 * 
	 * Method Name: dispatcher
	 * 
	 * Method Description: 
	 *  Take PCBptr and store its context to all hardware components. 
	 *  Copy CPU GPR register values from given PCBptr into the CPU registers.
	 *  Do the opposite operation of save context. The selected process has 
	 *  been given the CPU to run, so that's why we must restore its CPU
	 *  context from the PCB into the CPU registers.The OS code that performs 
	 *  this restore context process is called the dispatcher.  
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
		gpr[0] = hypoMainMemory[(int) (PCBptr + GPR0Index)];
		gpr[1] = hypoMainMemory[(int) (PCBptr + GPR1Index)];
		gpr[2] = hypoMainMemory[(int) (PCBptr + GPR2Index)];
		gpr[3] = hypoMainMemory[(int) (PCBptr + GPR3Index)]; 
		gpr[4] = hypoMainMemory[(int) (PCBptr + GPR4Index)];
		gpr[5] = hypoMainMemory[(int) (PCBptr + GPR5Index)];
		gpr[6] = hypoMainMemory[(int) (PCBptr + GPR6Index)];
		gpr[7] = hypoMainMemory[(int) (PCBptr + GPR7Index)];
		
		sp = hypoMainMemory[(int) (PCBptr + SPIndex)];
		pc = hypoMainMemory[(int) (PCBptr + PCIndex)];
		psr = UserMode;
	}



	/**
	 * Jon Petani wrote this method 
	 * 
	 * Method Name: terminateProcess
	 * 
	 * Method Description: 
	 *  Method terminates a process by freeing the stack and PCB memory 
	 *  so that another process can have space. Return stack memory 
	 *  using stack start address and stack size in the given PCB.
	 *  Return PCB memory using the PCBptr. Recover all resources 
	 *  allocated to the process. 
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
		freeUserMemory(hypoMainMemory[(int) (PCBptr + stackStartAddressIndex)],
				hypoMainMemory[(int) (PCBptr + stackSizeIndex)]);

		freeOSMemory(PCBptr, PCB_SIZE);
	}



	/**
	 * Jon Petani wrote this method 
	 * 
	 * Method Name: allocateOSMemory
	 * 
	 * Method Description: 
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
	 * Jon Petani wrote this method 
	 * 
	 * Method Name: freeOSMemory
	 * 
	 * Method Description: 
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

		else if(size < 1 || ((ptr + size) >= MAX_MEMORY_ADDRESS)) {
			System.out.println("\nError: Invalid memory size. Returning error code: " + ERROR_INVALID_SIZE_OR_MEMORY_ADDRESS);
			return ERROR_INVALID_SIZE_OR_MEMORY_ADDRESS;
		}

		hypoMainMemory[(int) ptr] = OSFreeList; // make the given free block point to free block pointed by OS free list
		hypoMainMemory[(int) ptr + 1] = size; // set the free block size in the given free block
		OSFreeList = ptr; // set OS free list point to the given free block

		return OK;
	}



	/**
	 * Jon Petani wrote this method  
	 * 
	 * Method Name: allocateUserMemory
	 * 
	 * Method Description: 
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
	 * Brian Perel wrote this method 
	 * 
	 * Method Name: freeUserMemory
	 * 
	 * Method Description: 
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
		else if(size < 1 || ((ptr + size) >= MAX_MEMORY_ADDRESS)) {
			System.out.println("Error: Invalid memory address, memory address given is outside memory address range. Returning error code: " + ERROR_INVALID_SIZE_OR_MEMORY_ADDRESS);
			return ERROR_INVALID_SIZE_OR_MEMORY_ADDRESS;
		}

		hypoMainMemory[(int) ptr] = UserFreeList;
		hypoMainMemory[(int) ptr + 1] = size;
		UserFreeList = ptr;

		return OK;
	}



	/**
	 * Jon Petani wrote this method 
	 * 
	 * Method Name: checkAndProcessInterrupt
	 *
	 * Method Description: 
	 *  Read interrupt ID number. Based on the interrupt ID,
	 *  service the interrupt type by calling appropriate method using switch.
	 *  The occurrence of an event raises an interrupt in the system, in MTOPS 
	 *  were dealing with software interrupts raised by system call. 
	 *  When the interrupt occurs, the OS gets control. 
	 *  Every time CPU scheduling is done the OS checks for an interrupt. 
	 *  Only 1 interrupt can occur at a given time in MTOPS 
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

			case 3: isrInputCompletionInterrupt(); // input operation completion - io_getc = ISR of interrupt reads 1 character from keyboard
					break;

			case 4: isrOutputCompletionInterrupt(); // output operation completion - io_putc = ISR of interrupt reads 1 character from keyboard 
					break;

			default: System.out.println("\nError: Invalid interrupt ID entered. Error code: " + ERROR_INVALID_ID); // invalid interrupt ID
					 break;
		}
		return interruptID;
	}



	/**
	 * Brian Perel wrote this method 
	 * 
	 * Method Name: isrRunProgramInterrupt
	 *
	 * Method Description: 
	 *  Run program interrupt service routine (ISR).
	 *  Read filename and create process. 
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
	 * Brian Perel wrote this method 
	 * 
	 * Method Name: isrInputCompletionInterrupt
	 * 
	 * Method Description: 
	 *  Method services a 'input completion' request.
	 *  By reading a PID entered by user, method searches the WQ for the PCB that 
	 *  is a match. Then read 1 character from the keyboard and store input character in GPR1 in the PCB of the process. 
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

		long PCBptr = searchAndRemovePCBFromWQ(PID); // search WQ to find the PCB having the given PID, then remove it 

		if(PCBptr > 0) {
			System.out.println("Enter a character to store: ");
			char inputCharacter = scan.next().charAt(0);
			// store inputCharacter in the GPR[1] in the PCB
			hypoMainMemory[(int) (PCBptr + GPR1Index)] = inputCharacter;
			hypoMainMemory[(int) (PCBptr + stateIndex)] = READY_STATE;
			insertIntoRQ(PCBptr);
		}
	}



	/**
	 * Brian Perel wrote this method 
	 * 
	 * Method Name: isrOutputCompletionInterrupt
	 * 
	 * Method Description: 
	 *  Method serves a 'input completion' request given by user. 
	 *  It reads a PID entered by user, searches the WQ for the PCB that matches,
	 *  then displays 1 character from the GPR1 in PCB of the process. 
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
			// then retrieve index 1 of GPR array and assign to variable
			char outputCharacter = (char) hypoMainMemory[(int) (PCBptr + GPR1Index)];

			System.out.println("Character in the GPR in PCB: " + outputCharacter);
			hypoMainMemory[(int) (PCBptr + stateIndex)] = READY_STATE;
			insertIntoRQ(PCBptr);
		}
	}



	/**
	 * Brian Perel wrote this method 
	 * 
	 * Method Name: isrShutdownSystem
	 *
	 * Method Description: 
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
			RQ = hypoMainMemory[(int) (ptr + nextPCBptrIndex)]; // RQ is set to next PCB using ptr
			terminateProcess(ptr); // terminate process
			ptr = RQ;
		}

		// terminate all processes in WQ one by one
		ptr = WQ;

		while(ptr != END_OF_LIST) {
			WQ = hypoMainMemory[(int) (ptr + nextPCBptrIndex)]; // RQ is set to next PCB using ptr
			terminateProcess(ptr); // terminate process
			ptr = WQ;
		}
	}



	/**
	 * Brian Perel wrote this method 
	 * 
	 * Method Name: searchAndRemovePCBFromWQ
	 *
	 * Method Description: 
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
			if(hypoMainMemory[(int) (currentPCBptr + PIDIndex)] == PID) {

				if(previousPCBptr == END_OF_LIST) { // match found, remove from WQ
					// first PCB
					WQ = hypoMainMemory[(int) (currentPCBptr + nextPCBptrIndex)];
				}

				else {
					// not first PCB
					hypoMainMemory[(int) (previousPCBptr + nextPCBptrIndex)] =
							hypoMainMemory[(int) (previousPCBptr + nextPCBptrIndex)];
				}

				hypoMainMemory[(int) (currentPCBptr + nextPCBptrIndex)] = END_OF_LIST;
				return currentPCBptr;
			}
			previousPCBptr = currentPCBptr;
			currentPCBptr = hypoMainMemory[(int) (currentPCBptr + nextPCBptrIndex)];
		}

		// No matching PCB is found, display PID message and return end of list code
		System.out.println("PID not found");
		return END_OF_LIST;
	}



	/**
	 * Jon Petani wrote this method 
	 * 
	 * Method Name: systemCall
	 * 
	 * Method Description: 
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
			case 9: status = io_putcSystemCall();
					break;
	
			// invalid system call ID
			default: System.out.println("Invalid system call ID error");
					break;
		}

		psr = UserMode;

		return status;
	}



	/**
	 * Jon Petani wrote this method 
	 * 
	 * Method Name: memAllocSystemCall
	 * 
	 * Method Description: 
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
		if(size < 1 || size > START_ADDR_OF_USER_FREELIST) {
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
	 * Jon Petani wrote this method 
	 * 
	 * Method Name: memFreeSystemCall
	 * 
	 * Method Description: 
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
		
		// check for size out of range
		if(size < 1 || size > START_ADDR_OF_USER_FREELIST) {
			System.out.println("The size of requested memory to be freed is out of range");
			return ERROR_INVALID_MEMORY_SIZE;
		}

		// check size of 1 and change it to 2
		if(size == 1) {
			size = 2;
		}

		gpr[0] = freeUserMemory(gpr[1], size);

		System.out.println(memFreeSystemCall() + "\n" + gpr[0] + gpr[1] + gpr[2] + "\n");
		return gpr[0];
	}



	/**
	 * Brian Perel wrote this method 
	 * 
	 * Method Name: io_getcSystemCall
	 * 
	 * Method Description: 
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
		return IO_GETCINTERRUPT;
	}



	/**
	 * Brian Perel wrote this method 
	 * 
	 * Method Name: io_putcSystemCall
	 * 
	 * Method Description: 
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
		return IO_PUTCINTERRUPT;
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