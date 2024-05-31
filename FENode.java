import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.thrift.server.*;
import org.apache.thrift.transport.*;
import org.apache.thrift.protocol.*;
import org.apache.thrift.*;

public class FENode {
    // Logger for logging messages
    static Logger log = Logger.getLogger(FENode.class.getName());

    // Main method: Entry point of the FE node application
    public static void main(String[] args) {
        // Configure logger to default settings
        BasicConfigurator.configure();

        // Check for correct number of command line arguments
        if (args.length != 1) {
            System.err.println("Usage: java FENode <FE_port>");
            System.exit(1);  // Exit if the argument count is incorrect
        }

        // Parse FE port from command line argument
        int portFE = Integer.parseInt(args[0]);
        log.info("Front-end node is set to listen on port " + portFE);

        try {
            // Create a non-blocking server socket on the specified FE port
            TNonblockingServerSocket serverSocket = new TNonblockingServerSocket(portFE);
            // Processor handles incoming requests using the implemented ForwardingHandler
            BcryptService.Processor<BcryptService.Iface> processor = new BcryptService.Processor<>(new ForwardingHandler());

            // Configure server arguments including protocol and transport factories
            THsHaServer.Args serverArgs = new THsHaServer.Args(serverSocket)
                .protocolFactory(new TBinaryProtocol.Factory())
                .transportFactory(new TFramedTransport.Factory())
                .processor(processor);

            // Create and start the half-sync/half-async server
            THsHaServer server = new THsHaServer(serverArgs);
            log.info("Starting the front-end server");
            server.serve();  // Start serving incoming requests
        } catch (Exception e) {
            log.error("Error setting up the server: ", e);  // Log any exceptions during setup
        }
    }

    // ForwardingHandler implements the BcryptService.Iface interface from Thrift
    static class ForwardingHandler implements BcryptService.Iface {
        // Queue to store and manage backend servers
        private final ConcurrentLinkedQueue<AddressPort> queue = new ConcurrentLinkedQueue<>();

        // Register backend servers to the front-end node
        @Override
        public void beToFeReg(String beHost, int bePort) throws TException {
            AddressPort newBackend = new AddressPort(beHost, bePort);  // Create a new backend server representation
            queue.add(newBackend);  // Add new backend to the queue
            log.info("Backend registered: " + beHost + ": " + bePort);  // Log the registration event
        }

        // Handle hash password requests by distributing the task to the least loaded backend
        @Override
        public List<String> hashPassword(List<String> passwords, short logRounds) throws IllegalArgument, TException {
            AddressPort backend = selectLeastLoadedNode();  // Select the least loaded backend server
            if (backend == null) {
                log.info("Handling hashPassword request locally on the FE node");
                BcryptServiceHandler handler = new BcryptServiceHandler();  // Fallback local handling if no backend is available
                return handler.hashPassword(passwords, logRounds);
            }

            log.info("Forwarding hashPassword request to backend: " + backend.host + ":" + backend.port);
            return forwardHashRequest(backend, passwords, logRounds);  // Forward the request to the selected backend
        }

        // Handle check password requests similarly by distributing the task
        @Override
        public List<Boolean> checkPassword(List<String> passwords, List<String> hashes) throws IllegalArgument, TException {
            AddressPort backend = selectLeastLoadedNode();  // Select the least loaded backend server

            if (backend == null) {
                log.info("No BENode available for work. FENode will handle sent checkPassword request");
                BcryptServiceHandler handler = new BcryptServiceHandler();  // Fallback local handling if no backend is available
                return handler.checkPassword(passwords, hashes);
            }

            log.info("Forwarding checkPassword request to backend: " + backend.host + ":" + backend.port);
            return forwardCheckRequest(backend, passwords, hashes);  // Forward the request to the selected backend
        }

        // Select the backend with the least active connections
        private AddressPort selectLeastLoadedNode() {
            return queue.stream()
                        .min((a, b) -> Integer.compare(a.activeConnections.get(), b.activeConnections.get()))
                        .orElse(null);
        }

        // Forward the hash request to the backend and return the result
        private List<String> forwardHashRequest(AddressPort backend, List<String> passwords, short logRounds) throws TException {
            try (TTransport transport = new TSocket(backend.host, backend.port)) {
                transport.open();
                TProtocol protocol = new TBinaryProtocol(new TFramedTransport(transport));
                BcryptService.Client client = new BcryptService.Client(protocol);

                backend.activeConnections.incrementAndGet();
                List<String> result = client.hashPassword(passwords, logRounds);
                backend.activeConnections.decrementAndGet();

                return result;
            } catch (Exception e) {
                backend.activeConnections.decrementAndGet();
                log.error("Failed to forward hashPassword request to backend", e);
                throw new TException("Failed to forward request to backend", e);
            }
        }

        // Forward the check password request to the backend and return the result
        private List<Boolean> forwardCheckRequest(AddressPort backend, List<String> passwords, List<String> hashes) throws TException {
            try (TTransport transport = new TSocket(backend.host, backend.port)) {
                transport.open();
                TProtocol protocol = new TBinaryProtocol(new TFramedTransport(transport));
                BcryptService.Client client = new BcryptService.Client(protocol);

                backend.activeConnections.incrementAndGet();
                List<Boolean> result = client.checkPassword(passwords, hashes);
                backend.activeConnections.decrementAndGet();

                return result;
            } catch (Exception e) {
                backend.activeConnections.decrementAndGet();
                log.error("Failed to forward checkPassword request to backend", e);
                throw new TException("Failed to forward request to backend", e);
            }
        }
    }

    // AddressPort class represents a backend server with its host, port, and count of active connections
    static class AddressPort {
        String host;  // Hostname of the backend server
        int port;    // Port number of the backend server
        AtomicInteger activeConnections;  // Thread-safe counter for active connections

        AddressPort(String host, int port) {
            this.host = host;
            this.port = port;
            this.activeConnections = new AtomicInteger(0);  // Initialize with zero active connections
        }
    }
}
