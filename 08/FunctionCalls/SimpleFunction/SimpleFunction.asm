(SimpleFunction.test)
@SP
A=M
D=A
@LCL
M=D
@SP
A=M
M=0
@SP
M=M+1
@SP
A=M
M=0
@SP
M=M+1
@LCL
D=M
@0
A=D+A
D=M
@SP
A=M
M=D
@SP
M=M+1
@LCL
D=M
@1
A=D+A
D=M
@SP
A=M
M=D
@SP
M=M+1
@SP
M=M-1
A=M
D=M
@SP
M=M-1
A=M
M=D+M
@SP
M=M+1
@SP
M=M-1
A=M
D=M
M=!D
@SP
M=M+1
@ARG
D=M
@0
A=D+A
D=M
@SP
A=M
M=D
@SP
M=M+1
@SP
M=M-1
A=M
D=M
@SP
M=M-1
A=M
M=D+M
@SP
M=M+1
@ARG
D=M
@1
A=D+A
D=M
@SP
A=M
M=D
@SP
M=M+1
@SP
M=M-1
A=M
D=M
@SP
M=M-1
A=M
M=M-D
@SP
M=M+1
@SP
M=M-1
A=M
D=M
@R14
M=D
@LCL
D=M
@R15
M=D
M=M-1
A=M
D=M
@THAT
M=D
@R15
M=M-1
A=M
D=M
@THIS
M=D
@R15
M=M-1
M=M-1
M=M-1
A=M
D=M
@R13
M=D
@R14
D=M
@ARG
A=M
M=D
@ARG
D=M
@R14
M=D
@R15
M=M+1
A=M
D=M
@LCL
M=D
@R15
M=M+1
A=M
D=M
@ARG
M=D
@R14
D=M
@SP
M=D
M=M+1
@R13
A=M
0;JMP