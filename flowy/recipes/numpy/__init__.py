from pythonforandroid.recipes.numpy import NumpyRecipe as _UpstreamNumpyRecipe


class NumpyRecipe(_UpstreamNumpyRecipe):
    # meson-python must be on hostpython's sys.path because build isolation
    # breaks under p4a's cross-compile hostpython3 (sys._home override),
    # which prevents `pyproject_hooks` from importing the `mesonpy` backend.
    hostpython_prerequisites = [
        "Cython>=3.0.6",
        "numpy",
        "meson-python>=0.15.0",
    ]

    # --no-isolation makes `python -m build` reuse hostpython's site-packages
    # instead of creating the broken isolated venv.
    extra_build_args = _UpstreamNumpyRecipe.extra_build_args + ["--no-isolation"]


recipe = NumpyRecipe()
