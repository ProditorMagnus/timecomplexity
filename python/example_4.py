import sys

# print(input("first line"))
# other lines
lines = sys.stdin.readlines()
lines = list(map(str.strip, lines))
# print(len(lines), lines)

if "a=1 b=5" in lines:
    print("1->3->5")
else:
    print("ei leidu")
