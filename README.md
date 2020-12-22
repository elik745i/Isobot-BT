# Isobot-BT

This is sketch and libraries for Arduino Nano v3.
BT module used in this project HC-05, which is hooked to arduino digital pins 3,4
Arduino, BT module powered streight from Li-Oin battery from old SAMSUNG phone, charging is done by charge&protection board.
In order to be able to program board and use robot's power switch, wire is soldered to switch's top contact and hooked to negative pole of arduino power supply through NPN (BC456) transistor through 1k resistor. 
This is kinda electronic switch so Arduino and BT module gets enough voltage.
Phototransmitting diod taken from old TV remote and is hooked to Arduino's digital pin 5.

For front cover 3D model (STL file) go to: https://www.thingiverse.com/thing:4692748

Video demonstration of the modified fully functional robot: https://www.youtube.com/watch?v=bPYBV43UScA

Control is done by sending command over BT serial: 0 to 138
