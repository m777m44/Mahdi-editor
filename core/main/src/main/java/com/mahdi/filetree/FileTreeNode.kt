package com.mahdi.filetree

import com.mahdi.file.FileObject

data class FileTreeNode(val file: FileObject, val isFile: Boolean, val isExpandable: Boolean, val name: String)
