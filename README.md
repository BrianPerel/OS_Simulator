# OS_Simulator
Program that simulates a real-time multitasking operating system for microcomputers. 
Hardware: MAR, MBR, IR, SP, PC, GPR (8 general purpose registers for gpr), PSR registers. RAM, Clock, CPU. 
I am simulating the hardware components by software.
No GUI interface for OS. 

methods: initializeSystem(), absoluteLoader(), cpu(), fetchOperand(), dumpMemory() 

Program Outline: 
1. Declare all hardware registers, components, error and non-error code constants 
2. call initializeSystem() to set all program hardware parts to 0 
3. read machine code file as input 
4. call absoluteLoader() with machine file input to load contents of file 
from disk to main memory 
5. assign return value from absolute loader to program counter  (this indicates loading the program counter) 
6. call dumpMemory() after loading machine program -> identifies errors and provides info about last state of the programs within OS before error encountered. Print out contents of GPR array, system clock, and memory addresses  
7. call CPU() to peform proper operation for given opcode value using a switch statement (execute machine program that was loaded into memory). The method executes 1 instruction at a time pointed by program counter performing the same fetch, decode, execute instructions each time. Fetch cycle = reads first word of instruction, decode cycle = decode first word of instruction into opcode, execute cycle = fetch operand values based on the opcode. 
-for every instruction in cpu(), fetchOperand() is called to perform switch operation on value from OpMode 
8. call dumpMemory() after executing machine program 
