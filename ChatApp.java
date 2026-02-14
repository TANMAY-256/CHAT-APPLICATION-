import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatApp {
    public static void main(String[] args) {
        System.out.println("=== Chat Application ===");
        System.out.println("1. Start Server");
        System.out.println("2. Start Client");
        System.out.print("Choose (1 or 2): ");
        try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
            String choice = console.readLine();
            if ("1".equals(choice)) {
                // Start the server
                ChatServer.main(new String[0]);
            } else if ("2".equals(choice)) {
                // Start a client
                ChatClient client = new ChatClient();
                client.start();
            } else {
                System.out.println("Invalid choice. Exiting.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ---------- Server Implementation ----------
    static class ChatServer {
        private static final int PORT = 12345;
        private static Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();
        private static List<String> messageHistory = new CopyOnWriteArrayList<>();

        public static void main(String[] args) {
            System.out.println("Chat Server started on port " + PORT);
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket);
                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    clients.add(clientHandler);
                    new Thread(clientHandler).start();
                }
            } catch (IOException e) {
                System.err.println("Server exception: " + e.getMessage());
            }
        }

        public static void broadcast(String message, ClientHandler sender) {
            for (ClientHandler client : clients) {
                if (client != sender) {
                    client.sendMessage(message);
                }
            }
            messageHistory.add(message);
            if (messageHistory.size() > 100) {
                messageHistory.remove(0);
            }
        }

        public static void removeClient(ClientHandler client) {
            clients.remove(client);
        }

        public static List<String> getMessageHistory() {
            return new ArrayList<>(messageHistory);
        }

        public static Set<String> getUserNames() {
            Set<String> names = new HashSet<>();
            for (ClientHandler client : clients) {
                names.add(client.getUserName());
            }
            return names;
        }
    }

    // Handler for each client (inner class of ChatServer)
    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String userName;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public String getUserName() {
            return userName;
        }

        public void sendMessage(String message) {
            out.println(message);
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                out.println("SUBMITNAME");
                userName = in.readLine();
                if (userName == null) return;
                System.out.println(userName + " joined.");

                out.println("HISTORY");
                for (String msg : ChatServer.getMessageHistory()) {
                    out.println(msg);
                }
                out.println("ENDHISTORY");

                ChatServer.broadcast(userName + " has joined.", this);

                String input;
                while ((input = in.readLine()) != null) {
                    if (input.startsWith("/users")) {
                        out.println("USERLIST");
                        for (String user : ChatServer.getUserNames()) {
                            out.println(user);
                        }
                        out.println("ENDUSERLIST");
                    } else if (input.startsWith("/quit")) {
                        break;
                    } else {
                        String fullMessage = userName + ": " + input;
                        ChatServer.broadcast(fullMessage, this);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error with client " + userName + ": " + e.getMessage());
            } finally {
                ChatServer.removeClient(this);
                ChatServer.broadcast(userName + " has left.", this);
                try {
                    socket.close();
                } catch (IOException e) {
                    // ignore
                }
                System.out.println(userName + " disconnected.");
            }
        }
    }

    // ---------- Client Implementation ----------
    static class ChatClient {
        private static final String SERVER_ADDRESS = "localhost";
        private static final int SERVER_PORT = 12345;

        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String userName;

        public void start() {
            try {
                socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                Thread fetcherThread = new Thread(new IncomingReader());
                fetcherThread.start();

                BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
                System.out.print("Enter your name: ");
                userName = consoleReader.readLine();
                out.println(userName);

                String userInput;
                while ((userInput = consoleReader.readLine()) != null) {
                    out.println(userInput);
                    if (userInput.equals("/quit")) {
                        break;
                    }
                }

                socket.close();
                System.exit(0);
            } catch (IOException e) {
                System.err.println("Client error: " + e.getMessage());
            }
        }

        private class IncomingReader implements Runnable {
            @Override
            public void run() {
                try {
                    String message;
                    while ((message = in.readLine()) != null) {
                        if (message.equals("SUBMITNAME")) {
                            // already handled
                        } else if (message.equals("HISTORY")) {
                            System.out.println("--- Message History ---");
                            while (!(message = in.readLine()).equals("ENDHISTORY")) {
                                System.out.println(message);
                            }
                            System.out.println("------------------------");
                        } else if (message.equals("USERLIST")) {
                            System.out.println("--- Online Users ---");
                            while (!(message = in.readLine()).equals("ENDUSERLIST")) {
                                System.out.println(message);
                            }
                            System.out.println("---------------------");
                        } else {
                            System.out.println(message);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Fetcher thread error: " + e.getMessage());
                }
            }
        }
    }
}