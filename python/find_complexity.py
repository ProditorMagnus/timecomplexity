import numpy as np
from big_o import big_o

ns = input()
time = input()

print(ns, time)

ns = list(map(int, ns.split(",")))
time = list(map(int, time.split(",")))
# ns = [0, 1, 3, 7, 15, 31, 63, 127, 255, 511, 1023, 1638, 2047, 3276, 4095, 4914, 6552, 8190, 8191, 9828, 11466, 13104,
#       14742, 16380, 16383, 18018, 19656, 21294, 22932, 24570, 26208, 27846, 29484, 31122, 32760, 32767]
# time = [0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1, 2, 5, 10, 20, 22, 38, 61, 98, 91, 114, 161, 194, 244, 366, 297, 351, 415, 477,
#         548, 633, 706, 812, 925, 966, 1186]
assert len(ns) == len(time)

# Prevent divide by zero error
highest_zero = 0
i = len(ns) - 1
for i in range(len(ns)):
    if ns[i] == 0 or time[i] == 0:
        highest_zero = i
ns = ns[highest_zero + 1:]
time = time[highest_zero + 1:]

ns = np.array(ns)
time = np.array(time)

o_class, all_classes = big_o.infer_big_o_class(ns, time)
print(o_class)
