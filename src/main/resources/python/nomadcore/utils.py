import readline # optional, will allow Up/Down/History in the console
import code

def goInteractive(locals):
    """Debugging function that when called stops execution and drops in an interactive loop.

Exiting the interpreter will continue execution.
call as follows:
    goInteractive(locals())
"""
    vars = globals().copy()
    vars.update(locals)
    shell = code.InteractiveConsole(vars)
    shell.interact()
