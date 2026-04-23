from os.path import dirname

from pythonforandroid.recipes.android import AndroidRecipe as _UpstreamAndroidRecipe
from pythonforandroid.recipes.android import __file__ as _UPSTREAM_RECIPE_FILE


class AndroidRecipe(_UpstreamAndroidRecipe):
    # Build isolation is broken under p4a's cross-compile hostpython3
    # ("Ignoring sys._home = value override" -> backend import fails),
    # so install build deps on hostpython and pass --no-isolation.
    hostpython_prerequisites = [
        "Cython>=0.29,<3.1",
        "setuptools>=58.0.0",
        "wheel",
    ]

    extra_build_args = list(_UpstreamAndroidRecipe.extra_build_args) + ["--no-isolation"]

    def get_recipe_dir(self):
        # IncludedFilesBehaviour copies `{get_recipe_dir()}/src` into the
        # build dir. The upstream android recipe ships that `src/` tree; our
        # local override directory doesn't, so we point path-lookups back at
        # the upstream recipe dir while keeping our Python overrides above.
        return dirname(_UPSTREAM_RECIPE_FILE)


recipe = AndroidRecipe()
