package com.greenfox.controllers;

import com.greenfox.exceptions.SimilarUserException;
import com.greenfox.exceptions.SimilarUserExceptionMessage;
import com.greenfox.exceptions.UsernameException;
import com.greenfox.exceptions.UsernameExceptionMessage;
import com.greenfox.model.Account;
import com.greenfox.model.Client;
import com.greenfox.model.ClientMessage;
import com.greenfox.model.Log;
import com.greenfox.model.Message;
import com.greenfox.model.Response;
import com.greenfox.repository.LogRepo;
import com.greenfox.repository.MessageRepo;
import com.greenfox.repository.UserRepo;
import com.greenfox.services.MessageService;
import com.greenfox.services.UserService;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

@Controller
public class MainController {

  LogRepo logRepo;
  UserRepo userRepo;
  UserService userService;
  MessageRepo messageRepo;

  @Autowired
  public MainController(LogRepo logRepo, UserRepo userRepo, UserService userService, MessageRepo messageRepo) {
    this.logRepo = logRepo;
    this.userRepo = userRepo;
    this.userService = userService;
    this.messageRepo = messageRepo;
  }

  @RequestMapping("/")
  public String main(HttpServletRequest request, Model model) {

    System.out.println(System.getenv("CHAT_APP_PEER_ADDRESS"));

    createLog(request,"INFO");

    Iterable<Message> messages = messageRepo.findAll();
    model.addAttribute("messages", messages);

    if (userService.getCurrentAccount() == null) {
      return "enter";
    } else {
      System.out.println(userService.getCurrentAccount().getUsername());
      return "index";
    }
  }

  @GetMapping("/enter")
  public String enter(HttpServletRequest request) {
    createLog(request,"INFO");
    return "enter";
  }

  @PostMapping("/enter")
  public String saveUser(HttpServletRequest request,
      @RequestParam(value = "username") String username) throws Exception {

    if (username.equals("")) { //if input field is empty
      throw new UsernameException();
    } else if (userRepo.count() == 0) { //if database is empty, create account
      System.out.println("Empty database case, create new account"); //log INFO
      Account account = new Account(username);   //Sad code, code duplicate because: cannot get into for cycle in empty database
      userService.setCurrentAccount(account);
      userRepo.save(account);
    } else {
      for (Account account : userRepo.findAll()) { //if find similar username
        if (account.getUsername().equals(username)) {
          System.out.println("EXCEPTION Find a similar username, please find another one"); //log ERROR
          throw new SimilarUserException();
        } else {
          System.out.println("Good choice Created a new account"); //log INFO
          Account newuser = new Account(username);
          userService.setCurrentAccount(account);
          userRepo.save(newuser);
        }
      }
    }

    createLog(request,"INFO");
    return "redirect:/";
  }

  @PostMapping("/update/{id}")
  public String update(HttpServletRequest request,
      @PathVariable long id, @RequestParam("username") String username) throws Exception {
    if (username.equals("")) {                //if input field is empty
      throw new UsernameException();
    } else {
      Account account = userRepo.findOne(id); //update in the Database;
      account.setUsername(username);
      userRepo.save(account);
      userService.getCurrentAccount().setUsername(username); //update for the View
    }

    createLog(request,"INFO");
    return "redirect:/";
  }

  @PostMapping("/send")
  public String send(HttpServletRequest request, Model model, @RequestParam("message") String message) {

    Message newMessage = new Message(userService.getCurrentAccount().getUsername(), message);

    messageRepo.save(newMessage);
    Iterable<Message> messages = messageRepo.findAllByOrderByTimestampAsc();
    model.addAttribute("messages", messages);

    RestTemplate restTemplate = new RestTemplate();
    restTemplate.postForObject(MessageService.CHAT_APP_PEER_ADDRESS,new ClientMessage(newMessage, new Client(MessageService.CHAT_APP_UNIQUE_ID)),Response.class);

    createLog(request,"INFO");
    return "index";
  }

  public void createLog(HttpServletRequest request, String loglevel) {
    Log log = new Log(request, loglevel); // logging
    System.out.println(log);
    logRepo.save(log);
  }

  @ModelAttribute
  public void add(Model model) {
    model.addAttribute("account", userService.getCurrentAccount());
  }


  @ExceptionHandler(UsernameException.class)
  public String UsernameException(HttpServletRequest request, Model model) {

    createLog(request,"ERROR");

    UsernameExceptionMessage error = new UsernameExceptionMessage("The username field is empty");
    System.out.println(error.getError());
    model.addAttribute("usernameerror",error);
    model.addAttribute("account", userService.getCurrentAccount());

    if (request.getServletPath().startsWith("/update")) {
      return "index";
    }
    return "enter";
  }

  @ExceptionHandler(SimilarUserException.class)
  public String SimilarUserException(HttpServletRequest request, Model model) {

    createLog(request,"ERROR");

    SimilarUserExceptionMessage error = new SimilarUserExceptionMessage("There is a similar user in the database, please find another one");
    System.out.println(error.getError());
    model.addAttribute("similarusererror", error);

    return "enter";
  }
}
