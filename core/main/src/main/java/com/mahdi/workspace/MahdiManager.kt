package com.mahdi.workspace

import com.mahdi.file.FileObject

object MahdiManager {
    /** Returns the `.mahdi` directory within the project root. */
    suspend fun getMahdiDir(projectRoot: FileObject): FileObject? {
        return projectRoot.getChild(".mahdi")
    }

    /** Returns the `runner.sh` file if it exists in `.mahdi`. */
    suspend fun getRunScript(projectRoot: FileObject): FileObject? {
        val mahdiDir = getMahdiDir(projectRoot)
        if (mahdiDir != null && mahdiDir.exists() && mahdiDir.isDirectory()) {
            val runScript = mahdiDir.getChild("runner.sh")
            if (runScript != null && runScript.exists() && runScript.isFile()) {
                return runScript
            }
        }
        return null
    }
}
