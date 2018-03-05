def start(n):
    if n < 1:
        return
    for i in range(n):
        start(n - 1)
