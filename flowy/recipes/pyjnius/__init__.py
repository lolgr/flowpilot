import glob
import shutil
from os.path import dirname, join

from pythonforandroid.recipes.pyjnius import PyjniusRecipe as _UpstreamPyjniusRecipe
from pythonforandroid.recipes.pyjnius import __file__ as _UPSTREAM_RECIPE_FILE


class PyjniusRecipe(_UpstreamPyjniusRecipe):
    # Build isolation is broken under p4a's cross-compile hostpython3
    # ("Ignoring sys._home = value override" -> backend import fails),
    # so install build deps on hostpython and pass --no-isolation.
    #
    # pyjnius' pyproject.toml declares `Cython~=3.1.2`. Numpy's earlier build
    # left 0.29.x and 3.2.x Cython dist-infos behind in the hostpython
    # site-packages, and with multiple dist-infos present importlib.metadata
    # picks up the first alphabetically and reports a non-matching version.
    # So we pin to 3.1.x here and scrub stale dist-infos before installing.
    hostpython_prerequisites = [
        "Cython~=3.1.2",
        "setuptools>=58.0.0",
        "wheel",
    ]

    extra_build_args = list(_UpstreamPyjniusRecipe.extra_build_args) + ["--no-isolation"]

    def get_recipe_dir(self):
        # Use upstream recipe dir so patches (use_cython.patch, etc.) resolve.
        return dirname(_UPSTREAM_RECIPE_FILE)

    def install_hostpython_prerequisites(self, packages=None, force_upgrade=True):
        for pattern in ("Cython-*.dist-info", "cython-*.dist-info"):
            for stale in glob.glob(join(self.hostpython_site_dir, pattern)):
                shutil.rmtree(stale, ignore_errors=True)
        super().install_hostpython_prerequisites(packages=packages, force_upgrade=force_upgrade)


recipe = PyjniusRecipe()
