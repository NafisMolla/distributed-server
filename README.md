# distributed-server


## Building Instructions

1. Navigate to the starter files directory:

```bash
cd starter_files
```

2. Make the build script executable:

```bash
chmod +x ./build.sh
```

3. Execute the build script to compile the project:

```bash
./build.sh
```

## Running Instructions

### Step 1: Start FENode (Frontend Node)

```bash
java -cp .:gen-java/:"lib/*":"jBCrypt-0.4/*" FENode 10834
```

### Step 2: Start BENode (Backend Node)

```bash
java -cp .:gen-java/:"lib/*":"jBCrypt-0.4/*" BENode localhost 10834 10835
```

### Step 3: Start BENode 2 (Backend Node)

```bash
java -cp .:gen-java/:"lib/*":"jBCrypt-0.4/*" BENode localhost 10834 10836
```

### Step 4: Start Client

```bash
java -cp .:gen-java/:"lib/*":"jBCrypt-0.4/*" Client localhost 10834 4 4 10
```

Note: Run each of the above commands in **separate** terminal windows.




### `Client.java`

**Overview:**
The `Client.java` file creates a multi-threaded client that can send concurrent requests to a frontend server for hashing and verifying passwords.

**Detailed Breakdown:**
- **Executor Service**: This class uses Java’s concurrency framework to handle multiple threads efficiently. It manages a fixed thread pool (`Executors.newFixedThreadPool(numThreads)`) that limits the number of threads to the specified number, preventing resource exhaustion.
- **Tasks**: Each thread runs an instance of `ClientTask`, which performs network operations, password generation, hashing, and validation. The tasks demonstrate how to handle network I/O and CPU-intensive operations like bcrypt hashing simultaneously.
- **Network Communication**: Establishes a connection using Thrift's `TSocket` and `TFramedTransport`. The latter is crucial for non-blocking servers as it frames the messages, allowing the server to know when one message ends and another begins, facilitating efficient message parsing.
- **Timing Execution**: The client measures the time it takes from submitting the first task to the completion of all tasks using `System.nanoTime()`, providing insights into the system's performance and scalability.

### `FENode.java`

**Overview:**
The `FENode.java` file functions as a gateway or a load balancer that manages backend servers, distributing tasks among them based on their load.

**Detailed Breakdown:**
- **Thrift Server Setup**: Initializes a `THsHaServer`, a server type provided by Apache Thrift ideal for services with high throughput and non-blocking I/O operations. It configures the server with `TNonblockingServerSocket`, which listens for connections without blocking, and a `TBinaryProtocol.Factory` for serializing messages.
- **Forwarding Handler**: Implements the `BcryptService.Iface` interface, particularly focusing on managing and distributing requests. If no backend servers are available, it can also process requests directly, making it resilient to backend failures.
- **Load Balancing**: Uses a `ConcurrentLinkedQueue` to store backend server information and selects the least loaded server based on a simple comparison of active connections. This approach ensures a fair distribution of work and improves overall system efficiency.
- **Dynamic Registration**: Backend servers can register and deregister dynamically, allowing the system to scale up or down according to demand.

### `BENode.java`

**Overview:**
The `BENode.java` file encapsulates the backend server functionality, handling the actual password hashing requests from the frontend server.

**Detailed Breakdown:**
- **Persistent Registration**: Implements a retry mechanism for registering with the frontend server using exponential backoff, enhancing the robustness of the server in unstable network conditions.
- **Server Configuration**: Like `FENode`, it uses `THsHaServer` configured for high performance and scalability. It is set up to handle multiple simultaneous connections and requests efficiently using a non-blocking socket.
- **Service Processing**: Once registered, it listens for requests and processes them using bcrypt, a CPU-intensive operation that benefits from the server’s capability to handle multiple threads (`maxWorkerThreads`).
- **Resilience and Fault Tolerance**: Incorporates detailed error handling and logging to manage and mitigate issues during operation effectively.

### Common Architectural Features

**Apache Thrift**: All components use Apache Thrift for defining and implementing the service interface. This framework supports efficient and language-agnostic RPC mechanisms.

**Scalability and Efficiency**: The architecture is designed to scale horizontally by adding more backend servers (`BENode`) as demand increases. The frontend server (`FENode`) balances the load across these backend servers, optimizing resource utilization and response times.

**Logging and Monitoring**: Extensive use of logging (via log4j) helps in monitoring the system’s health and performance, and debugging issues in production.

**Security and Maintenance**: While the focus is on performance and scalability, these files also lay the groundwork for incorporating security features (like secure transport layers) and easy maintenance due to their modular and clear structure.
