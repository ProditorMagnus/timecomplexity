from big_o import big_o
from big_o import datagen
import numpy as np


def find_max(x):
    """Find the maximum element in a list of positive integers."""
    max_ = 0
    for el in x:
        if el > max_:
            max_ = el
    return max_


def start(n):
    if n < 1:
        return
    for i in range(n):
        start(n - 1)


ns = np.array([2047, 4094, 4095, 6141, 8188, 8191, 10235, 12282, 14329, 16376, 16383,
               18423, 20470, 22517, 24564, 26611, 28658, 30705, 32752, 32767, 34799, 36846, 38893, 40940, 40959, 49151,
               65535])
time = np.array([6, 14, 28, 32, 54, 101, 95, 129, 180, 226, 400, 296, 361, 454, 525, 648, 734, 815,
                 905, 1104, 1155, 1255, 1414, 1523, 1461, 2095, 3616])
o_class, all_classes = big_o.infer_big_o_class(ns, time)
print(o_class)

# positive_int_generator = lambda n: big_o.datagen.integers(n, 0, 10000)
# positive_int_generator = lambda n: datagen.n_(n)
# best, others = big_o.big_o(start, positive_int_generator, n_repeats=1, min_n=1, max_n=10)
# print(best)
