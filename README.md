# Isobot-BT

This is sketch and libraries for Arduino Nano v3: https://www.aliexpress.com/item/32714947583.html?spm=a2g0o.productlist.0.0.405cd8828XKa1p&algo_pvid=d5acb32c-9e68-486c-9ca1-a061c698f223&algo_expid=d5acb32c-9e68-486c-9ca1-a061c698f223-0&btsid=0b0a556e16086289564812820e28d6&ws_ab_test=searchweb0_0,searchweb201602_,searchweb201603_

BT module -  HC-05: https://www.aliexpress.com/item/32990347631.html?spm=a2g0o.productlist.0.0.6c79684fOQjVLp&algo_pvid=1c5ce035-6a55-470d-a332-4e84a76d1614&algo_expid=1c5ce035-6a55-470d-a332-4e84a76d1614-2&btsid=0b0a555f16086288928425414e2b6b&ws_ab_test=searchweb0_0,searchweb201602_,searchweb201603_
which is hooked to arduino's digital pins 3,4
Arduino, BT module powered streight from Li-Ion battery from old SAMSUNG phone, which fits perfectly on isobot body.
Charging is done by charge&protection board: https://www.aliexpress.com/item/32986135934.html?srcSns=sns_WhatsApp&spreadType=socialShare&bizType=ProductDetail&social_params=40004855740&tt=MG&aff_platform=default&sk=_9ApnQR&aff_trace_key=b6b76121cb7547e4ad30e09c9223ffed-1608628871152-00981-_9ApnQR&shareId=40004855740&businessType=ProductDetail&platform=AE&terminal_id=3e952a3a07934284b8c43999900503f5

In order to be able to program board and use robot's power switch, wire is soldered to switch's top contact and hooked to negative pole of arduino power supply through NPN (BC456) transistor through 1k resistor. 
This is kinda electronic switch so Arduino and BT module gets enough voltage.
Phototransmitting diod taken from old TV remote and is hooked to Arduino's digital pin 5.

For front cover 3D model (STL file) go to: https://www.thingiverse.com/thing:4692748

Video demonstration of the modified fully functional robot: https://www.youtube.com/watch?v=bPYBV43UScA

Control is done by sending command over BT serial: 0 to 138
