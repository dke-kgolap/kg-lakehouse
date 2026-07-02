/**
 * Read a byte stream as NDJSON lines, incrementally. Decodes with a streaming
 * TextDecoder, buffers partial lines across chunks, and yields complete lines
 * as soon as a newline is seen — so the UI can fill in as data arrives.
 */
export async function* readLines(
  stream: ReadableStream<Uint8Array>,
  signal?: AbortSignal,
): AsyncGenerator<string> {
  const reader = stream.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  try {
    while (true) {
      if (signal?.aborted) return;
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });

      let newlineIdx: number;
      while ((newlineIdx = buffer.indexOf("\n")) >= 0) {
        const line = buffer.slice(0, newlineIdx);
        buffer = buffer.slice(newlineIdx + 1);
        yield line;
      }
    }
    // Flush any trailing bytes + final line with no newline terminator.
    buffer += decoder.decode();
    if (buffer.length > 0) yield buffer;
  } finally {
    reader.releaseLock();
  }
}
